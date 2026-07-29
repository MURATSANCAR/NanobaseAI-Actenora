package com.nanobaseai.actenora.microsoftconnection.infrastructure.mail;

import com.nanobaseai.actenora.microsoftconnection.application.model.OutlookDraftRequest;
import com.nanobaseai.actenora.microsoftconnection.application.model.OutlookDraftResult;
import com.nanobaseai.actenora.microsoftconnection.application.port.OutlookDraftGateway;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.GraphApiException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitedOutlookDraftGatewayTest {

    @Test
    void reusesIdempotentDraftAndLimitsDistinctCreations() {
        AtomicInteger calls = new AtomicInteger();
        OutlookDraftGateway transport = (tenantId, request) -> new OutlookDraftResult(
                "draft-" + calls.incrementAndGet(),
                "https://outlook.example/draft",
                false
        );
        RateLimitedOutlookDraftGateway gateway =
                new RateLimitedOutlookDraftGateway(transport, 1, Duration.ofMinutes(1));
        UUID tenantId = UUID.randomUUID();
        OutlookDraftRequest first = request("same-key");

        OutlookDraftResult created = gateway.create(tenantId, first);
        OutlookDraftResult reused = gateway.create(tenantId, first);

        assertEquals(created.providerMessageId(), reused.providerMessageId());
        assertTrue(reused.reused());
        assertEquals(1, calls.get());
        assertThrows(GraphApiException.class, () -> gateway.create(tenantId, request("new-key")));
    }

    @Test
    void coalescesConcurrentRequestsWithTheSameIdempotencyKey() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        OutlookDraftGateway transport = (tenantId, request) -> {
            calls.incrementAndGet();
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
            return new OutlookDraftResult("draft", "https://outlook.example/draft", false);
        };
        RateLimitedOutlookDraftGateway gateway =
                new RateLimitedOutlookDraftGateway(transport, 2, Duration.ofMinutes(1));
        UUID tenantId = UUID.randomUUID();
        OutlookDraftRequest request = request("same-key");
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> gateway.create(tenantId, request));
            entered.await();
            var second = pool.submit(() -> gateway.create(tenantId, request));
            release.countDown();

            assertEquals(first.get().providerMessageId(), second.get().providerMessageId());
            assertTrue(second.get().reused());
            assertEquals(1, calls.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void doesNotReplayAnAmbiguousFailedMutation() {
        AtomicInteger calls = new AtomicInteger();
        OutlookDraftGateway transport = (tenantId, request) -> {
            calls.incrementAndGet();
            throw GraphApiException.serverError(503, "ambiguous");
        };
        RateLimitedOutlookDraftGateway gateway =
                new RateLimitedOutlookDraftGateway(transport, 2, Duration.ofMinutes(1));
        UUID tenantId = UUID.randomUUID();
        OutlookDraftRequest request = request("ambiguous-key");

        assertThrows(GraphApiException.class, () -> gateway.create(tenantId, request));
        assertThrows(GraphApiException.class, () -> gateway.create(tenantId, request));

        assertEquals(1, calls.get());
    }

    private static OutlookDraftRequest request(String key) {
        return new OutlookDraftRequest(
                "mailbox",
                "Subject",
                "<p>Body</p>",
                List.of("person@example.com"),
                key
        );
    }
}
