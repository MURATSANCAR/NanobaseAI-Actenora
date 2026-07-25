package com.nanobaseai.actenora.aiprocessing.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.InferenceResult;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.InferenceStreamChunk;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.LocalModelProviderException;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderCapabilities;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderFailureCategory;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderHealth;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ResolvedInferenceInput;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.SafeInferenceLog;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.TokenEstimate;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.TokenUsage;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.WorkerRequestEnvelope;
import com.nanobaseai.actenora.aiprocessing.application.port.LocalModelProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * OpenAI-compatible HTTP adapter for local LLM runtimes.
 * Connect timeout is configured on {@link HttpClient}; per-request read/deadline
 * timeout uses {@link HttpRequest.Builder#timeout(Duration)}.
 */
public class OpenAiCompatibleLocalProvider implements LocalModelProvider {

    private final LocalProviderConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Semaphore concurrency;
    private final ConcurrentHashMap<UUID, AtomicBoolean> cancellations = new ConcurrentHashMap<>();
    private final AtomicReference<ProviderHealth> lastHealth =
            new AtomicReference<>(ProviderHealth.down("not probed", 0));
    private final AtomicBoolean draining = new AtomicBoolean(false);

    public OpenAiCompatibleLocalProvider(LocalProviderConfig config) {
        this(config, defaultHttpClient(config), new ObjectMapper());
    }

    OpenAiCompatibleLocalProvider(LocalProviderConfig config, HttpClient httpClient, ObjectMapper objectMapper) {
        this.config = Objects.requireNonNull(config, "config");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.concurrency = new Semaphore(config.maxConcurrency(), true);
    }

    private static HttpClient defaultHttpClient(LocalProviderConfig config) {
        return HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public void beginDrain() {
        draining.set(true);
    }

    @Override
    public boolean isDraining() {
        return draining.get();
    }

    public ProviderHealth lastHealth() {
        return lastHealth.get();
    }

    @Override
    public InferenceResult submitInference(WorkerRequestEnvelope envelope, ResolvedInferenceInput input) {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(input, "input");
        long started = System.nanoTime();
        SafeInferenceLog.started(envelope, config.providerKind());
        acquireOrReject(envelope);
        AtomicBoolean cancelled = registerCancellation(envelope.attemptId());
        try {
            ensureNotCancelled(cancelled);
            rejectIfDraining();
            ensureServedModelAllowed(envelope.servedModelId());
            ensureHealthyForWork();

            ObjectNode body = buildChatBody(envelope, input, false);
            byte[] payload = objectMapper.writeValueAsBytes(body);

            Duration requestTimeout = effectiveReadTimeout(envelope.timeoutSeconds());
            HttpRequest request = HttpRequest.newBuilder(chatCompletionsUri())
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                    .build();

            ensureNotCancelled(cancelled);
            HttpResponse<String> response = send(request, envelope);
            ensureNotCancelled(cancelled);

            if (response.statusCode() == 404) {
                throw fail(envelope, ProviderFailureCategory.INVALID_SERVED_MODEL,
                        "served model not found at runtime: " + envelope.servedModelId(), false, started);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw fail(envelope, ProviderFailureCategory.PROVIDER_ERROR,
                        "provider HTTP status=" + response.statusCode(), true, started);
            }

            ParsedChatCompletion parsed = parseCompletion(response.body(), envelope, started);
            detectModelMismatch(envelope, parsed.model(), started);

            TokenUsage usage = parsed.usage();
            long latencyMs = elapsedMs(started);
            SafeInferenceLog.completed(envelope, usage, latencyMs);
            return new InferenceResult(
                    envelope.jobId(),
                    envelope.attemptId(),
                    parsed.model() == null ? envelope.servedModelId() : parsed.model(),
                    parsed.content(),
                    usage,
                    latencyMs
            );
        } catch (LocalModelProviderException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw fail(envelope, ProviderFailureCategory.CANCELLED, "inference interrupted", false, started, ex);
        } catch (Exception ex) {
            throw mapTransport(envelope, ex, started);
        } finally {
            cancellations.remove(envelope.attemptId());
            concurrency.release();
        }
    }

    @Override
    public Stream<InferenceStreamChunk> streamInference(
            WorkerRequestEnvelope envelope,
            ResolvedInferenceInput input
    ) {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(input, "input");
        if (!config.streamingEnabled()) {
            throw LocalModelProviderException.of(
                    ProviderFailureCategory.STREAMING_NOT_SUPPORTED,
                    "streaming disabled for provider " + config.providerKind(),
                    false
            );
        }

        long started = System.nanoTime();
        SafeInferenceLog.started(envelope, config.providerKind());
        acquireOrReject(envelope);
        AtomicBoolean cancelled = registerCancellation(envelope.attemptId());

        try {
            ensureNotCancelled(cancelled);
            rejectIfDraining();
            ensureServedModelAllowed(envelope.servedModelId());
            ensureHealthyForWork();

            ObjectNode body = buildChatBody(envelope, input, true);
            byte[] payload = objectMapper.writeValueAsBytes(body);
            Duration requestTimeout = effectiveReadTimeout(envelope.timeoutSeconds());
            HttpRequest request = HttpRequest.newBuilder(chatCompletionsUri())
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                    .build();

            HttpResponse<InputStream> response = sendStream(request, envelope);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                throw fail(envelope, ProviderFailureCategory.PROVIDER_ERROR,
                        "provider HTTP status=" + response.statusCode(), true, started);
            }

            List<InferenceStreamChunk> chunks = readSse(response.body(), envelope, cancelled, started);
            TokenUsage usage = chunks.stream()
                    .filter(InferenceStreamChunk::done)
                    .map(InferenceStreamChunk::tokenUsage)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(TokenUsage.unknown());
            SafeInferenceLog.completed(envelope, usage, elapsedMs(started));
            return chunks.stream();
        } catch (LocalModelProviderException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw fail(envelope, ProviderFailureCategory.CANCELLED, "stream interrupted", false, started, ex);
        } catch (Exception ex) {
            throw mapTransport(envelope, ex, started);
        } finally {
            cancellations.remove(envelope.attemptId());
            concurrency.release();
        }
    }

    @Override
    public ProviderHealth health() {
        long started = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(modelsUri())
                    .timeout(config.connectTimeout().plus(Duration.ofSeconds(2)))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = elapsedMs(started);
            ProviderHealth health;
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                if (latency >= config.degradedProbeThresholdMs()) {
                    health = ProviderHealth.degraded("probe slow latencyMs=" + latency, latency);
                } else {
                    health = ProviderHealth.up("models endpoint ok", latency);
                }
            } else {
                health = ProviderHealth.down("models HTTP status=" + response.statusCode(), latency);
            }
            lastHealth.set(health);
            SafeInferenceLog.health(config.providerKind(), health);
            return health;
        } catch (Exception ex) {
            ProviderHealth health = ProviderHealth.down(
                    "probe failed: " + ex.getClass().getSimpleName(),
                    elapsedMs(started)
            );
            lastHealth.set(health);
            SafeInferenceLog.health(config.providerKind(), health);
            return health;
        }
    }

    @Override
    public ProviderCapabilities capabilities() {
        Set<String> models = new LinkedHashSet<>(config.knownServedModelIds());
        return new ProviderCapabilities(
                config.providerKind(),
                config.streamingEnabled(),
                true,
                config.maxConcurrency(),
                models
        );
    }

    @Override
    public void cancel(UUID attemptId) {
        Objects.requireNonNull(attemptId, "attemptId");
        AtomicBoolean flag = cancellations.get(attemptId);
        if (flag != null) {
            flag.set(true);
        }
    }

    @Override
    public TokenEstimate estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return TokenEstimate.approximate(0);
        }
        // Local heuristic (~4 chars / token). Exact tokenizer stays in runtime adapters later.
        int tokens = Math.max(1, (int) Math.ceil(text.length() / 4.0));
        return TokenEstimate.approximate(tokens);
    }

    private void acquireOrReject(WorkerRequestEnvelope envelope) {
        if (!concurrency.tryAcquire()) {
            throw fail(envelope, ProviderFailureCategory.CONCURRENCY_LIMIT,
                    "provider concurrency limit reached max=" + config.maxConcurrency(), true, System.nanoTime());
        }
    }

    private void rejectIfDraining() {
        if (draining.get()) {
            throw LocalModelProviderException.of(
                    ProviderFailureCategory.DRAINING,
                    "provider is draining and rejects new work",
                    true
            );
        }
    }

    private void ensureHealthyForWork() {
        ProviderHealth health = lastHealth.get();
        if (health.status() == com.nanobaseai.actenora.aiprocessing.application.modelworker.HealthStatus.DOWN
                || health.status() == com.nanobaseai.actenora.aiprocessing.application.modelworker.HealthStatus.DEGRADED) {
            // Allow first call before any probe; only block when an explicit probe reported bad health.
            if (!"not probed".equals(health.detail())) {
                throw LocalModelProviderException.of(
                        ProviderFailureCategory.HEALTH_DEGRADED,
                        "provider health is " + health.status(),
                        true
                );
            }
        }
    }

    private void ensureServedModelAllowed(String servedModelId) {
        if (!config.knownServedModelIds().isEmpty()
                && !config.knownServedModelIds().contains(servedModelId)) {
            throw LocalModelProviderException.of(
                    ProviderFailureCategory.INVALID_SERVED_MODEL,
                    "served model not configured on provider: " + servedModelId,
                    false
            );
        }
    }

    private void detectModelMismatch(WorkerRequestEnvelope envelope, String responseModel, long started) {
        if (responseModel == null || responseModel.isBlank()) {
            return;
        }
        if (!responseModel.equals(envelope.servedModelId())) {
            throw fail(envelope, ProviderFailureCategory.MODEL_MISMATCH,
                    "response model does not match servedModelId expected="
                            + envelope.servedModelId() + " actual=" + responseModel,
                    false,
                    started);
        }
    }

    private ObjectNode buildChatBody(
            WorkerRequestEnvelope envelope,
            ResolvedInferenceInput input,
            boolean stream
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", envelope.servedModelId());
        body.put("stream", stream);
        ArrayNode messages = body.putArray("messages");
        if (input.systemPrompt() != null && !input.systemPrompt().isBlank()) {
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", input.systemPrompt());
        }
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", input.userPrompt());
        envelope.generationParameters().toOpenAiBodyFields().forEach((key, value) -> {
            if ("stream".equals(key)) {
                return;
            }
            body.set(key, objectMapper.valueToTree(value));
        });
        body.put("stream", stream);
        return body;
    }

    private ParsedChatCompletion parseCompletion(String responseBody, WorkerRequestEnvelope envelope, long started) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root == null || !root.isObject()) {
                throw fail(envelope, ProviderFailureCategory.MALFORMED_RESPONSE,
                        "response root is not an object", false, started);
            }
            String model = textOrNull(root.get("model"));
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                throw fail(envelope, ProviderFailureCategory.MALFORMED_RESPONSE,
                        "response missing choices", false, started);
            }
            JsonNode message = choices.get(0).get("message");
            if (message == null || message.get("content") == null || message.get("content").isNull()) {
                throw fail(envelope, ProviderFailureCategory.MALFORMED_RESPONSE,
                        "response missing message.content", false, started);
            }
            String content = message.get("content").asText();
            TokenUsage usage = TokenUsage.unknown();
            JsonNode usageNode = root.get("usage");
            if (usageNode != null && usageNode.isObject()) {
                int in = usageNode.path("prompt_tokens").asInt(0);
                int out = usageNode.path("completion_tokens").asInt(0);
                usage = TokenUsage.of(Math.max(0, in), Math.max(0, out));
            }
            return new ParsedChatCompletion(model, content, usage);
        } catch (LocalModelProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw fail(envelope, ProviderFailureCategory.MALFORMED_RESPONSE,
                    "unable to parse chat completion JSON", false, started, ex);
        }
    }

    private List<InferenceStreamChunk> readSse(
            InputStream body,
            WorkerRequestEnvelope envelope,
            AtomicBoolean cancelled,
            long started
    ) throws IOException {
        List<InferenceStreamChunk> chunks = new ArrayList<>();
        String responseModel = null;
        int completionTokens = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                ensureNotCancelled(cancelled);
                if (line.isBlank()) {
                    continue;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) {
                    chunks.add(InferenceStreamChunk.done(TokenUsage.of(0, completionTokens)));
                    break;
                }
                JsonNode root = objectMapper.readTree(data);
                if (responseModel == null) {
                    responseModel = textOrNull(root.get("model"));
                    if (responseModel != null) {
                        detectModelMismatch(envelope, responseModel, started);
                    }
                }
                JsonNode choices = root.get("choices");
                if (choices != null && choices.isArray() && !choices.isEmpty()) {
                    JsonNode delta = choices.get(0).path("delta").path("content");
                    if (!delta.isMissingNode() && !delta.isNull()) {
                        chunks.add(InferenceStreamChunk.delta(delta.asText()));
                        completionTokens += estimateTokens(delta.asText()).tokens();
                    }
                }
            }
        }
        if (chunks.isEmpty() || !chunks.get(chunks.size() - 1).done()) {
            chunks.add(InferenceStreamChunk.done(TokenUsage.of(0, completionTokens)));
        }
        return chunks;
    }

    private HttpResponse<String> send(HttpRequest request, WorkerRequestEnvelope envelope)
            throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<InputStream> sendStream(HttpRequest request, WorkerRequestEnvelope envelope)
            throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    private LocalModelProviderException mapTransport(
            WorkerRequestEnvelope envelope,
            Exception ex,
            long started
    ) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (ex instanceof java.net.http.HttpConnectTimeoutException
                || root instanceof java.net.http.HttpConnectTimeoutException) {
            return fail(envelope, ProviderFailureCategory.CONNECT_TIMEOUT,
                    "connect timeout", true, started, ex);
        }
        if (ex instanceof HttpTimeoutException || root instanceof HttpTimeoutException) {
            return fail(envelope, ProviderFailureCategory.READ_TIMEOUT,
                    "read/request timeout", true, started, ex);
        }
        if (ex instanceof ConnectException || root instanceof ConnectException) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            if (msg.contains("timed out") || msg.contains("timeout")) {
                return fail(envelope, ProviderFailureCategory.CONNECT_TIMEOUT,
                        "connect timeout", true, started, ex);
            }
            return fail(envelope, ProviderFailureCategory.CONNECTION_FAILURE,
                    "connection failure", true, started, ex);
        }
        if (ex instanceof IOException) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            if (msg.contains("connection") || msg.contains("connect")) {
                return fail(envelope, ProviderFailureCategory.CONNECTION_FAILURE,
                        "connection failure", true, started, ex);
            }
        }
        return fail(envelope, ProviderFailureCategory.UNKNOWN,
                "unexpected provider failure: " + ex.getClass().getSimpleName(), true, started, ex);
    }

    private LocalModelProviderException fail(
            WorkerRequestEnvelope envelope,
            ProviderFailureCategory category,
            String message,
            boolean retryable,
            long started
    ) {
        return fail(envelope, category, message, retryable, started, null);
    }

    private LocalModelProviderException fail(
            WorkerRequestEnvelope envelope,
            ProviderFailureCategory category,
            String message,
            boolean retryable,
            long started,
            Throwable cause
    ) {
        SafeInferenceLog.failed(envelope, category, retryable, elapsedMs(started));
        if (cause == null) {
            return LocalModelProviderException.of(category, message, retryable);
        }
        return LocalModelProviderException.of(category, message, retryable, cause);
    }

    private AtomicBoolean registerCancellation(UUID attemptId) {
        AtomicBoolean flag = new AtomicBoolean(false);
        cancellations.put(attemptId, flag);
        return flag;
    }

    private static void ensureNotCancelled(AtomicBoolean cancelled) {
        if (cancelled.get()) {
            throw LocalModelProviderException.of(
                    ProviderFailureCategory.CANCELLED,
                    "inference cancelled",
                    false
            );
        }
    }

    private Duration effectiveReadTimeout(int timeoutSeconds) {
        Duration jobTimeout = Duration.ofSeconds(timeoutSeconds);
        return jobTimeout.compareTo(config.readTimeout()) < 0 ? jobTimeout : config.readTimeout();
    }

    private URI chatCompletionsUri() {
        return config.baseUrl().resolve("/v1/chat/completions");
    }

    private URI modelsUri() {
        return config.baseUrl().resolve("/v1/models");
    }

    private static long elapsedMs(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private record ParsedChatCompletion(String model, String content, TokenUsage usage) {
    }
}
