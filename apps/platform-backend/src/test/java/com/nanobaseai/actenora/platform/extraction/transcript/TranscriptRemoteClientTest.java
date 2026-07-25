package com.nanobaseai.actenora.platform.extraction.transcript;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptRemoteClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger hits = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        hits.set(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/transcripts/upload", exchange -> {
            int n = hits.incrementAndGet();
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            if (n < 3) {
                exchange.sendResponseHeaders(503, body.length);
            } else {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(201, body.length);
            }
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void retriesTransientFailuresThenSucceeds() {
        TranscriptRemoteProperties props = new TranscriptRemoteProperties();
        props.setBaseUrl(baseUrl);
        props.setConnectTimeout(Duration.ofSeconds(2));
        props.setReadTimeout(Duration.ofSeconds(5));
        props.setMaxRetries(3);
        props.setRetryBackoff(Duration.ofMillis(10));

        AtomicInteger attempts = new AtomicInteger();
        TranscriptRemoteClient client = new TranscriptRemoteClient(props, attempts);

        TranscriptRemoteClient.RemoteResponse response = client.upload(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "a.vtt",
                "text/vtt",
                "WEBVTT\n".getBytes(StandardCharsets.UTF_8),
                null,
                null);

        assertEquals(201, response.statusCode());
        assertTrue(attempts.get() >= 3);
        assertEquals(3, hits.get());
    }

    @Test
    void exhaustsRetriesOnPersistentFailure() {
        server.removeContext("/api/v1/transcripts/upload");
        server.createContext("/api/v1/transcripts/upload", exchange -> {
            hits.incrementAndGet();
            byte[] body = "down".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        TranscriptRemoteProperties props = new TranscriptRemoteProperties();
        props.setBaseUrl(baseUrl);
        props.setConnectTimeout(Duration.ofSeconds(1));
        props.setReadTimeout(Duration.ofSeconds(2));
        props.setMaxRetries(2);
        props.setRetryBackoff(Duration.ofMillis(5));

        TranscriptRemoteClient client = new TranscriptRemoteClient(props);
        assertThrows(
                TranscriptRemoteClient.TransientRemoteException.class,
                () -> client.upload(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "a.vtt",
                        "text/vtt",
                        "WEBVTT\n".getBytes(StandardCharsets.UTF_8),
                        null,
                        null));
        assertEquals(3, hits.get());
    }
}
