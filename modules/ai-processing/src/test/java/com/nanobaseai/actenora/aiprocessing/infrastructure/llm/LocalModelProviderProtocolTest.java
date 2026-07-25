package com.nanobaseai.actenora.aiprocessing.infrastructure.llm;

import com.nanobaseai.actenora.aiprocessing.application.modelworker.GenerationParameters;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.HealthStatus;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.InferenceResult;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.InferenceStreamChunk;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.LocalModelProviderException;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ModelWorkerSession;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderFailureCategory;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderHealth;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ResolvedInferenceInput;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.WorkerHeartbeat;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.WorkerRequestEnvelope;
import com.nanobaseai.actenora.aiprocessing.domain.routing.InferenceTaskType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalModelProviderProtocolTest {

    private HttpServer server;
    private URI baseUrl;
    private final AtomicReference<Handler> chatHandler = new AtomicReference<>();
    private final AtomicReference<Handler> modelsHandler = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> dispatch(chatHandler.get(), exchange));
        server.createContext("/v1/models", exchange -> dispatch(modelsHandler.get(), exchange));
        server.start();
        baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        modelsHandler.set(exchange -> writeJson(exchange, 200, "{\"data\":[{\"id\":\"local-qwen\"}]}"));
        chatHandler.set(exchange -> writeJson(exchange, 200, okCompletion("local-qwen", "{\"topics\":[]}")));
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void submitInferenceSucceedsAgainstOpenAiCompatibleEndpoint() {
        OpenAiCompatibleLocalProvider provider = openAiProvider(Duration.ofSeconds(2), Duration.ofSeconds(5), 2);
        InferenceResult result = provider.submitInference(envelope("local-qwen", 5), input());
        assertEquals("local-qwen", result.servedModelId());
        assertTrue(result.content().contains("topics"));
        assertTrue(result.tokenUsage().totalTokens() >= 0);
    }

    @Test
    void readTimeoutIsClassifiedSeparatelyFromConnect() {
        chatHandler.set(exchange -> {
            try {
                Thread.sleep(2_500);
                writeJson(exchange, 200, okCompletion("local-qwen", "late"));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        OpenAiCompatibleLocalProvider provider = openAiProvider(Duration.ofSeconds(2), Duration.ofMillis(200), 2);
        LocalModelProviderException ex = assertThrows(
                LocalModelProviderException.class,
                () -> provider.submitInference(envelope("local-qwen", 5), input())
        );
        assertEquals(ProviderFailureCategory.READ_TIMEOUT, ex.category());
        assertTrue(ex.retryable());
    }

    @Test
    void connectionFailureWhenEndpointClosed() {
        OpenAiCompatibleLocalProvider provider = new OpenAiCompatibleLocalProvider(
                LocalProviderConfig.builder("openai-compatible", URI.create("http://127.0.0.1:1"))
                        .connectTimeout(Duration.ofMillis(200))
                        .readTimeout(Duration.ofSeconds(1))
                        .maxConcurrency(1)
                        .knownServedModelIds(Set.of("local-qwen"))
                        .build()
        );
        LocalModelProviderException ex = assertThrows(
                LocalModelProviderException.class,
                () -> provider.submitInference(envelope("local-qwen", 5), input())
        );
        assertTrue(
                ex.category() == ProviderFailureCategory.CONNECTION_FAILURE
                        || ex.category() == ProviderFailureCategory.CONNECT_TIMEOUT,
                () -> "unexpected category " + ex.category()
        );
    }

    @Test
    void malformedResponseIsSafeFailure() {
        chatHandler.set(exchange -> writeJson(exchange, 200, "{\"not\":\"a-completion\"}"));
        OpenAiCompatibleLocalProvider provider = openAiProvider(Duration.ofSeconds(2), Duration.ofSeconds(5), 2);
        LocalModelProviderException ex = assertThrows(
                LocalModelProviderException.class,
                () -> provider.submitInference(envelope("local-qwen", 5), input())
        );
        assertEquals(ProviderFailureCategory.MALFORMED_RESPONSE, ex.category());
        assertFalse(ex.getMessage().toLowerCase().contains("prompt"));
    }

    @Test
    void cancellationStopsMockInference() throws Exception {
        MockLocalProvider mock = new MockLocalProvider(1, true, Set.of("mock-model"));
        mock.setArtificialDelayMs(2_000);
        WorkerRequestEnvelope env = envelope("mock-model", 10);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = executor.submit(() -> mock.submitInference(env, input()));
            Thread.sleep(50);
            mock.cancel(env.attemptId());
            LocalModelProviderException ex = assertThrows(LocalModelProviderException.class, () -> {
                try {
                    future.get(3, TimeUnit.SECONDS);
                } catch (Exception e) {
                    if (e.getCause() instanceof LocalModelProviderException lmpe) {
                        throw lmpe;
                    }
                    throw new RuntimeException(e);
                }
            });
            assertEquals(ProviderFailureCategory.CANCELLED, ex.category());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void drainingRejectsNewWorkAndHeartbeatReportsState() throws Exception {
        MockLocalProvider mock = new MockLocalProvider(2, true, Set.of("mock-model"));
        ModelWorkerSession worker = new ModelWorkerSession("worker-1", mock, 2);
        worker.probeHealth();
        mock.setArtificialDelayMs(300);
        WorkerRequestEnvelope running = envelope("mock-model", 10);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        try {
            Future<InferenceResult> inFlight = executor.submit(() -> {
                started.countDown();
                return worker.submit(running, input());
            });
            assertTrue(started.await(1, TimeUnit.SECONDS));
            Thread.sleep(30);
            worker.beginDrain();
            WorkerHeartbeat heartbeat = worker.heartbeat();
            assertTrue(heartbeat.draining());
            assertTrue(heartbeat.inFlight() >= 1);

            LocalModelProviderException rejected = assertThrows(
                    LocalModelProviderException.class,
                    () -> worker.submit(envelope("mock-model", 10), input())
            );
            assertEquals(ProviderFailureCategory.DRAINING, rejected.category());
            assertTrue(worker.awaitQuiescence(Duration.ofSeconds(2)));
            assertEquals("{\"ok\":true}", inFlight.get().content());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void healthDegradedBlocksInference() {
        MockLocalProvider mock = new MockLocalProvider();
        mock.setHealth(ProviderHealth.degraded("latency high", 50));
        LocalModelProviderException ex = assertThrows(
                LocalModelProviderException.class,
                () -> mock.submitInference(envelope("mock-model", 5), input())
        );
        assertEquals(ProviderFailureCategory.HEALTH_DEGRADED, ex.category());
    }

    @Test
    void openAiHealthDegradedBlocksAfterProbe() {
        modelsHandler.set(exchange -> writeJson(exchange, 503, "{\"error\":\"down\"}"));
        OpenAiCompatibleLocalProvider provider = new OpenAiCompatibleLocalProvider(
                LocalProviderConfig.builder("openai-compatible", baseUrl)
                        .connectTimeout(Duration.ofSeconds(2))
                        .readTimeout(Duration.ofSeconds(5))
                        .maxConcurrency(2)
                        .degradedProbeThresholdMs(1)
                        .knownServedModelIds(Set.of("local-qwen"))
                        .build()
        );
        ProviderHealth health = provider.health();
        assertEquals(HealthStatus.DOWN, health.status());
        LocalModelProviderException ex = assertThrows(
                LocalModelProviderException.class,
                () -> provider.submitInference(envelope("local-qwen", 5), input())
        );
        assertEquals(ProviderFailureCategory.HEALTH_DEGRADED, ex.category());
    }

    @Test
    void wrongServedModelIdIsRejected() {
        MockLocalProvider mock = new MockLocalProvider(1, true, Set.of("mock-model"));
        LocalModelProviderException ex = assertThrows(
                LocalModelProviderException.class,
                () -> mock.submitInference(envelope("other-model", 5), input())
        );
        assertEquals(ProviderFailureCategory.INVALID_SERVED_MODEL, ex.category());
    }

    @Test
    void modelMismatchWhenResponseModelDiffers() {
        chatHandler.set(exchange -> writeJson(exchange, 200, okCompletion("other-runtime-model", "x")));
        OpenAiCompatibleLocalProvider provider = openAiProvider(Duration.ofSeconds(2), Duration.ofSeconds(5), 2);
        LocalModelProviderException ex = assertThrows(
                LocalModelProviderException.class,
                () -> provider.submitInference(envelope("local-qwen", 5), input())
        );
        assertEquals(ProviderFailureCategory.MODEL_MISMATCH, ex.category());
    }

    @Test
    void concurrencyLimitProvidesBackpressure() throws Exception {
        MockLocalProvider mock = new MockLocalProvider(1, true, Set.of("mock-model"));
        mock.setArtificialDelayMs(400);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstStarted = new CountDownLatch(1);
        AtomicInteger rejections = new AtomicInteger();
        try {
            Future<?> first = executor.submit(() -> {
                firstStarted.countDown();
                return mock.submitInference(envelope("mock-model", 10), input());
            });
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS));
            Thread.sleep(40);
            try {
                mock.submitInference(envelope("mock-model", 10), input());
            } catch (LocalModelProviderException ex) {
                assertEquals(ProviderFailureCategory.CONCURRENCY_LIMIT, ex.category());
                rejections.incrementAndGet();
            }
            first.get(2, TimeUnit.SECONDS);
            assertEquals(1, rejections.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void mockConnectTimeoutAndConnectionFailure() {
        MockLocalProvider timeout = new MockLocalProvider();
        timeout.setConnectDelayMs(10);
        timeout.forceFailure(ProviderFailureCategory.CONNECT_TIMEOUT);
        assertEquals(
                ProviderFailureCategory.CONNECT_TIMEOUT,
                assertThrows(
                        LocalModelProviderException.class,
                        () -> timeout.submitInference(envelope("mock-model", 5), input())
                ).category()
        );

        MockLocalProvider conn = new MockLocalProvider();
        conn.setConnectDelayMs(10);
        conn.forceFailure(ProviderFailureCategory.CONNECTION_FAILURE);
        assertEquals(
                ProviderFailureCategory.CONNECTION_FAILURE,
                assertThrows(
                        LocalModelProviderException.class,
                        () -> conn.submitInference(envelope("mock-model", 5), input())
                ).category()
        );
    }

    @Test
    void streamingIsOptionalAndTokenEstimateIsSafe() {
        MockLocalProvider mock = new MockLocalProvider(1, true, Set.of("mock-model"));
        mock.setResponse("abcdef");
        String joined = mock.streamInference(envelope("mock-model", 5), input())
                .map(InferenceStreamChunk::delta)
                .collect(Collectors.joining());
        assertEquals("abcdef", joined);

        MockLocalProvider noStream = new MockLocalProvider(1, false, Set.of("mock-model"));
        assertEquals(
                ProviderFailureCategory.STREAMING_NOT_SUPPORTED,
                assertThrows(
                        LocalModelProviderException.class,
                        () -> noStream.streamInference(envelope("mock-model", 5), input())
                ).category()
        );

        assertTrue(mock.estimateTokens("abcd").approximate());
        assertEquals(1, mock.estimateTokens("abcd").tokens());
    }

    @Test
    void llamaCppAndVllmAdaptersDelegate() {
        LlamaCppProvider llama = new LlamaCppProvider(
                LocalProviderConfig.builder("llamacpp", baseUrl)
                        .connectTimeout(Duration.ofSeconds(2))
                        .readTimeout(Duration.ofSeconds(5))
                        .knownServedModelIds(Set.of("local-qwen"))
                        .build()
        );
        VllmProvider vllm = new VllmProvider(
                LocalProviderConfig.builder("vllm", baseUrl)
                        .connectTimeout(Duration.ofSeconds(2))
                        .readTimeout(Duration.ofSeconds(5))
                        .knownServedModelIds(Set.of("local-qwen"))
                        .build()
        );
        assertEquals("llamacpp", llama.capabilities().providerKind());
        assertEquals("vllm", vllm.capabilities().providerKind());
        assertEquals("local-qwen", llama.submitInference(envelope("local-qwen", 5), input()).servedModelId());
        assertEquals("local-qwen", vllm.submitInference(envelope("local-qwen", 5), input()).servedModelId());
    }

    private OpenAiCompatibleLocalProvider openAiProvider(
            Duration connect,
            Duration read,
            int concurrency
    ) {
        return new OpenAiCompatibleLocalProvider(
                LocalProviderConfig.builder("openai-compatible", baseUrl)
                        .connectTimeout(connect)
                        .readTimeout(read)
                        .maxConcurrency(concurrency)
                        .knownServedModelIds(Set.of("local-qwen"))
                        .build()
        );
    }

    private static WorkerRequestEnvelope envelope(String servedModelId, int timeoutSeconds) {
        return WorkerRequestEnvelope.builder()
                .jobId(UUID.randomUUID())
                .attemptId(UUID.randomUUID())
                .taskType(InferenceTaskType.CHUNK_EXTRACTION)
                .modelId(UUID.randomUUID())
                .servedModelId(servedModelId)
                .promptVersion("p1")
                .schemaVersion("s1")
                .timeoutSeconds(timeoutSeconds)
                .generationParameters(GenerationParameters.builder().temperature(0.0).maxTokens(128).build())
                .build();
    }

    private static ResolvedInferenceInput input() {
        return ResolvedInferenceInput.of("system", "user prompt must never be logged");
    }

    private static String okCompletion(String model, String content) {
        return """
                {
                  "id":"chatcmpl-1",
                  "model":"%s",
                  "choices":[{"index":0,"message":{"role":"assistant","content":%s},"finish_reason":"stop"}],
                  "usage":{"prompt_tokens":11,"completion_tokens":7,"total_tokens":18}
                }
                """.formatted(model, jsonString(content));
    }

    private static String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static void dispatch(Handler handler, HttpExchange exchange) throws IOException {
        if (handler == null) {
            writeJson(exchange, 500, "{\"error\":\"no handler\"}");
            return;
        }
        handler.handle(exchange);
    }

    private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
