package com.nanobaseai.actenora.aiprocessing.infrastructure.llm;

import com.nanobaseai.actenora.aiprocessing.application.modelworker.LocalModelProviderException;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderFailureCategory;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ResolvedInferenceInput;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.WorkerRequestEnvelope;
import com.nanobaseai.actenora.aiprocessing.domain.routing.InferenceTaskType;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskTypeConcurrencyLimitTest {

    private HttpServer server;
    private URI baseUrl;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger maxFinalInFlight = new AtomicInteger();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            int now = inFlight.incrementAndGet();
            maxFinalInFlight.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(80);
                byte[] body = """
                        {"id":"1","model":"test-model","choices":[{"message":{"role":"assistant","content":"{}"}}],
                        "usage":{"prompt_tokens":1,"completion_tokens":1}}
                        """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exchange.sendResponseHeaders(500, -1);
            } finally {
                inFlight.decrementAndGet();
            }
        });
        server.start();
        baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void finalNoteConcurrencyIsCappedAtOne() throws Exception {
        OpenAiCompatibleLocalProvider provider = new OpenAiCompatibleLocalProvider(
                LocalProviderConfig.builder("openai-compatible", baseUrl)
                        .maxConcurrency(4)
                        .maxConcurrencyExtraction(4)
                        .maxConcurrencyFinal(1)
                        .knownServedModelIds(java.util.Set.of("test-model"))
                        .build()
        );

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch started = new CountDownLatch(1);
        try {
            Future<?> first = pool.submit(() -> {
                started.countDown();
                provider.submitInference(envelope(InferenceTaskType.FINAL_NOTE), ResolvedInferenceInput.of("s", "u"));
                return null;
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            Thread.sleep(20);
            LocalModelProviderException rejected = assertThrows(
                    LocalModelProviderException.class,
                    () -> provider.submitInference(
                            envelope(InferenceTaskType.FINAL_NOTE),
                            ResolvedInferenceInput.of("s", "u")
                    )
            );
            assertEquals(ProviderFailureCategory.CONCURRENCY_LIMIT, rejected.category());
            first.get(5, TimeUnit.SECONDS);
            assertTrue(maxFinalInFlight.get() <= 1);
        } finally {
            pool.shutdownNow();
        }
    }

    private static WorkerRequestEnvelope envelope(InferenceTaskType taskType) {
        return WorkerRequestEnvelope.builder()
                .jobId(UUID.randomUUID())
                .attemptId(UUID.randomUUID())
                .taskType(taskType)
                .modelId(UUID.randomUUID())
                .servedModelId("test-model")
                .promptVersion("pv")
                .schemaVersion("sv")
                .timeoutSeconds(30)
                .build();
    }
}
