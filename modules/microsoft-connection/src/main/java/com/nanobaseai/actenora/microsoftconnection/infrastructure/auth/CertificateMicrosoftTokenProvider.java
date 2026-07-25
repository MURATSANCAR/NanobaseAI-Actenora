package com.nanobaseai.actenora.microsoftconnection.infrastructure.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobaseai.actenora.microsoftconnection.application.model.AccessToken;
import com.nanobaseai.actenora.microsoftconnection.application.port.MicrosoftTokenProvider;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.GraphApiException;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Production Microsoft token provider using certificate-based client assertion.
 */
public final class CertificateMicrosoftTokenProvider implements MicrosoftTokenProvider {

    private final CertificateCredential credential;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final InstantClock clock;
    private final AtomicReference<AccessToken> cached = new AtomicReference<>();

    public CertificateMicrosoftTokenProvider(CertificateCredential credential, InstantClock clock) {
        this(
                credential,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                new ObjectMapper(),
                clock
        );
    }

    CertificateMicrosoftTokenProvider(
            CertificateCredential credential,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            InstantClock clock
    ) {
        this.credential = Objects.requireNonNull(credential, "credential");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AccessToken getAccessToken() {
        AccessToken current = cached.get();
        Instant now = clock.now();
        if (current != null && !current.isExpiredOrNearExpiry(now, 60)) {
            return current;
        }
        return refreshAccessToken();
    }

    @Override
    public AccessToken refreshAccessToken() {
        Instant now = clock.now();
        String assertion = ClientAssertionJwtFactory.create(
                credential,
                now,
                ClientAssertionJwtFactory.DurationOrSeconds.ofSeconds(600)
        );
        String form = "client_id=" + enc(credential.clientId())
                + "&scope=" + enc(credential.scope())
                + "&client_assertion_type="
                + enc("urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                + "&client_assertion=" + enc(assertion)
                + "&grant_type=client_credentials";
        AccessToken token = requestToken(form);
        cached.set(token);
        return token;
    }

    private AccessToken requestToken(String formBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(credential.tokenEndpoint()))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw GraphApiException.tokenFailure(
                        "Token endpoint failed status=" + response.statusCode() + " body=" + response.body()
                );
            }
            JsonNode root = objectMapper.readTree(response.body());
            String accessToken = root.path("access_token").asText(null);
            long expiresIn = root.path("expires_in").asLong(3600);
            if (accessToken == null || accessToken.isBlank()) {
                throw GraphApiException.tokenFailure("Token response missing access_token");
            }
            return new AccessToken(accessToken, clock.now().plusSeconds(Math.max(60, expiresIn)));
        } catch (GraphApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw GraphApiException.tokenFailure("Token acquisition failed: " + ex.getMessage());
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
