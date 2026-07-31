package com.nanobaseai.actenora.microsoftconnection.infrastructure.graph;

import com.nanobaseai.actenora.microsoftconnection.application.model.AccessToken;
import com.nanobaseai.actenora.microsoftconnection.application.port.MicrosoftTokenProvider;
import com.nanobaseai.actenora.microsoftconnection.application.port.GraphTelemetry;
import com.nanobaseai.actenora.sharedkernel.messaging.ExponentialBackoff;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Graph HTTP executor with 401 token refresh, 404 availability retry, 429 Retry-After, and 5xx backoff.
 */
public final class GraphHttpClient {

    private final URI graphBaseUrl;
    private final MicrosoftTokenProvider tokenProvider;
    private final HttpClient httpClient;
    private final GraphSleeper sleeper;
    private final ExponentialBackoff serverBackoff;
    private final int maxAttempts;
    private final int notFoundMaxAttempts;
    private final GraphEgressPolicy egressPolicy;
    private final GraphTelemetry telemetry;

    public GraphHttpClient(
            URI graphBaseUrl,
            MicrosoftTokenProvider tokenProvider,
            HttpClient httpClient,
            GraphSleeper sleeper,
            ExponentialBackoff serverBackoff,
            int maxAttempts,
            int notFoundMaxAttempts
    ) {
        this(
                graphBaseUrl,
                tokenProvider,
                httpClient,
                sleeper,
                serverBackoff,
                maxAttempts,
                notFoundMaxAttempts,
                GraphEgressPolicy.defaults(),
                GraphTelemetry.NOOP);
    }

    public GraphHttpClient(
            URI graphBaseUrl,
            MicrosoftTokenProvider tokenProvider,
            HttpClient httpClient,
            GraphSleeper sleeper,
            ExponentialBackoff serverBackoff,
            int maxAttempts,
            int notFoundMaxAttempts,
            GraphEgressPolicy egressPolicy
    ) {
        this(
                graphBaseUrl,
                tokenProvider,
                httpClient,
                sleeper,
                serverBackoff,
                maxAttempts,
                notFoundMaxAttempts,
                egressPolicy,
                GraphTelemetry.NOOP);
    }

    public GraphHttpClient(
            URI graphBaseUrl,
            MicrosoftTokenProvider tokenProvider,
            HttpClient httpClient,
            GraphSleeper sleeper,
            ExponentialBackoff serverBackoff,
            int maxAttempts,
            int notFoundMaxAttempts,
            GraphEgressPolicy egressPolicy,
            GraphTelemetry telemetry
    ) {
        this.graphBaseUrl = Objects.requireNonNull(graphBaseUrl, "graphBaseUrl");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        this.serverBackoff = Objects.requireNonNull(serverBackoff, "serverBackoff");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (notFoundMaxAttempts < 1) {
            throw new IllegalArgumentException("notFoundMaxAttempts must be >= 1");
        }
        this.maxAttempts = maxAttempts;
        this.notFoundMaxAttempts = notFoundMaxAttempts;
        this.egressPolicy = Objects.requireNonNull(egressPolicy, "egressPolicy");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.egressPolicy.assertAllowed(graphBaseUrl);
    }

    public static GraphHttpClient defaults(URI graphBaseUrl, MicrosoftTokenProvider tokenProvider) {
        return new GraphHttpClient(
                graphBaseUrl,
                tokenProvider,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                GraphSleeper.THREAD,
                new ExponentialBackoff(Duration.ofMillis(10), Duration.ofSeconds(2), 0.0),
                5,
                3
        );
    }

    /**
     * Builds a request against the Graph base URL (egress-checked).
     */
    public HttpRequest.Builder newRequest(String absoluteOrRelative) {
        return HttpRequest.newBuilder(resolve(absoluteOrRelative)).timeout(Duration.ofSeconds(30));
    }

    /**
     * Sends a pre-built request builder, injecting a fresh bearer token on each attempt.
     */
    public HttpResponse<String> send(HttpRequest.Builder requestBuilder) {
        Objects.requireNonNull(requestBuilder, "requestBuilder");
        HttpRequest prototype = requestBuilder.build();
        return send(token -> copyWithBearer(prototype, token), false);
    }

    public HttpResponse<String> send(Function<AccessToken, HttpRequest> requestFactory) {
        return send(requestFactory, false);
    }

    /**
     * Sends a non-idempotent request at most once. Ambiguous transport and 5xx
     * failures are surfaced to the caller without replaying the mutation.
     */
    public HttpResponse<String> sendAtMostOnce(Function<AccessToken, HttpRequest> requestFactory) {
        Objects.requireNonNull(requestFactory, "requestFactory");
        if (!telemetry.allowRequest()) {
            throw GraphApiException.circuitOpen();
        }
        AccessToken token = tokenProvider.getAccessToken();
        HttpRequest request = requestFactory.apply(token);
        Instant attemptStartedAt = Instant.now();
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            telemetry.recordHttp(status, Duration.between(attemptStartedAt, Instant.now()));
            if (status >= 200 && status < 300) {
                return response;
            }
            if (status == 401) {
                tokenProvider.refreshAccessToken();
                throw GraphApiException.unauthorized("Graph unauthorized; token refreshed for a safe caller retry");
            }
            if (status == 403) {
                throw GraphApiException.configuration(
                        "Graph configuration/permission error: " + truncate(response.body()));
            }
            if (status == 404) {
                throw GraphApiException.notFound("Graph resource not found: " + request.uri());
            }
            if (status == 429) {
                throw GraphApiException.rateLimited(
                        "Graph rate limited",
                        parseRetryAfter(response).orElse(Duration.ofSeconds(2)));
            }
            if (status >= 500) {
                throw GraphApiException.serverError(status, "Graph server error status=" + status);
            }
            throw new GraphApiException(
                    "GRAPH_CLIENT_ERROR",
                    "Graph client error status=" + status + " body=" + truncate(response.body()),
                    status,
                    null,
                    false);
        } catch (GraphApiException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw GraphApiException.transport("Graph request interrupted", ex);
        } catch (IOException ex) {
            telemetry.recordHttp(0, Duration.between(attemptStartedAt, Instant.now()));
            throw GraphApiException.transport("Graph transport failure", ex);
        }
    }

    private static HttpRequest copyWithBearer(HttpRequest prototype, AccessToken token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(prototype.uri())
                .timeout(prototype.timeout().orElse(Duration.ofSeconds(30)))
                .method(
                        prototype.method(),
                        prototype.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody())
                );
        prototype.headers().map().forEach((name, values) -> {
            if ("Authorization".equalsIgnoreCase(name)) {
                return;
            }
            for (String value : values) {
                builder.header(name, value);
            }
        });
        builder.header("Authorization", "Bearer " + token.value());
        builder.header("Accept", "application/json");
        return builder.build();
    }

    /**
     * @param retryNotFound when true, 404 is retried (transcript availability lag).
     */
    public HttpResponse<String> send(Function<AccessToken, HttpRequest> requestFactory, boolean retryNotFound) {
        Objects.requireNonNull(requestFactory, "requestFactory");
        if (!telemetry.allowRequest()) {
            throw GraphApiException.circuitOpen();
        }
        int attempt = 0;
        int notFoundAttempts = 0;
        boolean refreshedOn401 = false;
        GraphApiException last = null;

        while (attempt < maxAttempts) {
            AccessToken token = tokenProvider.getAccessToken();
            HttpRequest request = requestFactory.apply(token);
            Instant attemptStartedAt = Instant.now();
            try {
                HttpResponse<String> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );
                int status = response.statusCode();
                telemetry.recordHttp(status, Duration.between(attemptStartedAt, Instant.now()));
                if (status >= 200 && status < 300) {
                    return response;
                }
                if (status == 401) {
                    if (!refreshedOn401) {
                        tokenProvider.refreshAccessToken();
                        refreshedOn401 = true;
                        attempt++;
                        continue;
                    }
                    throw GraphApiException.unauthorized("Graph unauthorized after token refresh");
                }
                if (status == 403) {
                    throw GraphApiException.configuration(
                            "Graph configuration/permission error: " + truncate(response.body())
                    );
                }
                if (status == 404) {
                    notFoundAttempts++;
                    if (retryNotFound && notFoundAttempts < notFoundMaxAttempts) {
                        sleepQuietly(serverBackoff.delayForAttempt(notFoundAttempts - 1));
                        attempt++;
                        continue;
                    }
                    throw GraphApiException.notFound("Graph resource not found: " + request.uri());
                }
                if (status == 429) {
                    Duration retryAfter = parseRetryAfter(response).orElse(Duration.ofSeconds(2));
                    last = GraphApiException.rateLimited("Graph rate limited", retryAfter);
                    sleepQuietly(retryAfter);
                    attempt++;
                    continue;
                }
                if (status >= 500) {
                    last = GraphApiException.serverError(status, "Graph server error status=" + status);
                    sleepQuietly(serverBackoff.delayForAttempt(attempt));
                    attempt++;
                    continue;
                }
                throw new GraphApiException(
                        "GRAPH_CLIENT_ERROR",
                        "Graph client error status=" + status + " body=" + truncate(response.body()),
                        status,
                        null,
                        false
                );
            } catch (GraphApiException ex) {
                throw ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw GraphApiException.transport("Graph request interrupted", ex);
            } catch (IOException ex) {
                telemetry.recordHttp(0, Duration.between(attemptStartedAt, Instant.now()));
                last = GraphApiException.transport("Graph transport failure", ex);
                sleepQuietly(serverBackoff.delayForAttempt(attempt));
                attempt++;
            }
        }
        if (last != null) {
            throw last;
        }
        throw GraphApiException.serverError(500, "Graph retries exhausted");
    }

    public URI resolve(String absoluteOrRelative) {
        Objects.requireNonNull(absoluteOrRelative, "absoluteOrRelative");
        URI resolved;
        if (absoluteOrRelative.startsWith("http://") || absoluteOrRelative.startsWith("https://")) {
            resolved = URI.create(absoluteOrRelative);
        } else {
            String base = graphBaseUrl.toString();
            if (!base.endsWith("/")) {
                base = base + "/";
            }
            String path = absoluteOrRelative.startsWith("/") ? absoluteOrRelative.substring(1) : absoluteOrRelative;
            resolved = URI.create(base + path);
        }
        egressPolicy.assertAllowed(resolved);
        return resolved;
    }

    public URI graphBaseUrl() {
        return graphBaseUrl;
    }

    public HttpRequest authorizedGet(String absoluteOrRelative, AccessToken token) {
        return authorizedGet(absoluteOrRelative, token, "application/json");
    }

    public HttpRequest authorizedGet(String absoluteOrRelative, AccessToken token, String accept) {
        return HttpRequest.newBuilder(resolve(absoluteOrRelative))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token.value())
                .header("Accept", accept == null || accept.isBlank() ? "application/json" : accept)
                .GET()
                .build();
    }

    public HttpRequest authorizedJson(
            String absoluteOrRelative,
            String method,
            String jsonBody,
            AccessToken token
    ) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(resolve(absoluteOrRelative))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token.value())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
        HttpRequest.BodyPublisher body = jsonBody == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8);
        return builder.method(method, body).build();
    }

    private void sleepQuietly(Duration duration) {
        try {
            sleeper.sleep(duration == null ? Duration.ZERO : duration);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw GraphApiException.transport("Interrupted during Graph backoff", ex);
        }
    }

    private static Optional<Duration> parseRetryAfter(HttpResponse<?> response) {
        return response.headers().firstValue("Retry-After").map(value -> {
            try {
                return Duration.ofSeconds(Long.parseLong(value.trim()));
            } catch (NumberFormatException ignored) {
                return Duration.ofSeconds(2);
            }
        });
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 256 ? body : body.substring(0, 256);
    }
}
