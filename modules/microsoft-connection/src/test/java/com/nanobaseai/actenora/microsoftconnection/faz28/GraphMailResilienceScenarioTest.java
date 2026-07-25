package com.nanobaseai.actenora.microsoftconnection.faz28;

import com.nanobaseai.actenora.microsoftconnection.application.model.AccessToken;
import com.nanobaseai.actenora.microsoftconnection.application.model.MailSendRequest;
import com.nanobaseai.actenora.microsoftconnection.application.model.MailSendResult;
import com.nanobaseai.actenora.microsoftconnection.application.port.MailGateway;
import com.nanobaseai.actenora.microsoftconnection.application.port.MicrosoftTokenProvider;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.GraphApiException;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.GraphHttpClient;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.GraphSleeper;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.mail.RateLimitedMailGateway;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.notification.InMemoryNotificationInbox;
import com.nanobaseai.actenora.sharedkernel.messaging.ExponentialBackoff;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FAZ 28: Graph 429, mail rate limit, duplicate Graph notification.
 */
class GraphMailResilienceScenarioTest {

    private HttpServer server;
    private URI baseUri;
    private final AtomicInteger hits = new AtomicInteger();
    private int rateLimitRemaining;

    @BeforeEach
    void setUp() throws IOException {
        hits.set(0);
        rateLimitRemaining = 2;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1.0/me", exchange -> {
            hits.incrementAndGet();
            if (rateLimitRemaining > 0) {
                rateLimitRemaining--;
                byte[] body = "{\"error\":\"throttled\"}".getBytes();
                exchange.getResponseHeaders().add("Retry-After", "0");
                exchange.sendResponseHeaders(429, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
                return;
            }
            byte[] ok = "{\"id\":\"ok\"}".getBytes();
            exchange.sendResponseHeaders(200, ok.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(ok);
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1.0");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void graph429_retriesWithRetryAfterThenSucceeds() {
        MicrosoftTokenProvider tokens = new FixedTokens();
        GraphHttpClient client = new GraphHttpClient(
                baseUri,
                tokens,
                HttpClient.newHttpClient(),
                GraphSleeper.NOOP,
                new ExponentialBackoff(Duration.ofMillis(1), Duration.ofMillis(5), 0.0),
                5,
                3,
                com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.GraphEgressPolicy.localTesting()
        );

        var response = client.send(client.newRequest("/me").GET());
        assertEquals(200, response.statusCode());
        assertTrue(hits.get() >= 3);
    }

    @Test
    void mailRateLimit_blocksBurstBeyondWindow() {
        AtomicInteger sends = new AtomicInteger();
        MailGateway delegate = (tenantId, request) -> {
            sends.incrementAndGet();
            return MailSendResult.accepted("msg-" + sends.get());
        };
        RateLimitedMailGateway gateway = new RateLimitedMailGateway(delegate, 2, Duration.ofMinutes(1));
        UUID tenant = UUID.randomUUID();

        gateway.send(tenant, MailSendRequest.of("user", "s1", "<p>a</p>", List.of("a@example.com")));
        gateway.send(tenant, MailSendRequest.of("user", "s2", "<p>b</p>", List.of("b@example.com")));
        GraphApiException limited = assertThrows(
                GraphApiException.class,
                () -> gateway.send(tenant, MailSendRequest.of("user", "s3", "<p>c</p>", List.of("c@example.com")))
        );
        assertEquals(GraphApiException.CODE_RATE_LIMITED, limited.code());
        assertEquals(429, limited.statusCode());
        assertEquals(1, gateway.rateLimitedCount());
        assertEquals(2, sends.get());
    }

    @Test
    void mailIdempotency_sameKeyDoesNotDoubleSend() {
        AtomicInteger sends = new AtomicInteger();
        MailGateway delegate = (tenantId, request) -> {
            sends.incrementAndGet();
            return MailSendResult.accepted("provider-" + sends.get());
        };
        RateLimitedMailGateway gateway = new RateLimitedMailGateway(delegate, 10, Duration.ofMinutes(1));
        UUID tenant = UUID.randomUUID();
        MailSendRequest request = new MailSendRequest(
                "user", "Subject", "<p>body</p>", List.of("a@example.com"), "idem-key-1"
        );
        MailSendResult a = gateway.send(tenant, request);
        MailSendResult b = gateway.send(tenant, request);
        assertEquals(a.providerMessageId(), b.providerMessageId());
        assertEquals(1, sends.get());
    }

    @Test
    void duplicateGraphNotification_processedOnce() {
        InMemoryNotificationInbox inbox = new InMemoryNotificationInbox();
        AtomicInteger handled = new AtomicInteger();
        String notificationId = "notif-123";

        if (inbox.claim("graph-webhook", notificationId)) {
            handled.incrementAndGet();
        }
        if (inbox.claim("graph-webhook", notificationId)) {
            handled.incrementAndGet();
        }
        assertEquals(1, handled.get());
        assertEquals(1, inbox.size());
    }

    private static final class FixedTokens implements MicrosoftTokenProvider {
        @Override
        public AccessToken getAccessToken() {
            return new AccessToken("token", Instant.now().plusSeconds(3600));
        }

        @Override
        public AccessToken refreshAccessToken() {
            return getAccessToken();
        }
    }
}
