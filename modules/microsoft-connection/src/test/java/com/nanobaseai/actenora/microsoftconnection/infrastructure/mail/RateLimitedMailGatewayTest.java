package com.nanobaseai.actenora.microsoftconnection.infrastructure.mail;

import com.nanobaseai.actenora.microsoftconnection.application.model.MailSendRequest;
import com.nanobaseai.actenora.microsoftconnection.application.model.MailSendResult;
import com.nanobaseai.actenora.microsoftconnection.application.port.MailGateway;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.GraphApiException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimitedMailGatewayTest {

    @Test
    void enforcesTenantWindowAndDeduplicatesIdempotencyKey() {
        AtomicInteger sends = new AtomicInteger();
        MailGateway delegate = (tenantId, request) -> {
            sends.incrementAndGet();
            return MailSendResult.accepted("provider-" + sends.get());
        };
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-07-26T08:00:00Z"));
        RateLimitedMailGateway gateway = new RateLimitedMailGateway(
                delegate, 1, Duration.ofMinutes(1), now::get);
        UUID tenantId = UUID.randomUUID();
        MailSendRequest first = request("same-key");

        assertEquals("provider-1", gateway.send(tenantId, first).providerMessageId());
        assertEquals("provider-1", gateway.send(tenantId, first).providerMessageId());
        assertEquals(1, sends.get());
        assertThrows(GraphApiException.class, () -> gateway.send(tenantId, request("other-key")));

        now.set(now.get().plus(Duration.ofMinutes(1)));
        gateway.send(tenantId, request("other-key"));
        assertEquals(2, sends.get());
    }

    private static MailSendRequest request(String idempotencyKey) {
        return new MailSendRequest(
                "sender@contoso.com",
                "Subject",
                "Body",
                List.of("recipient@contoso.com"),
                idempotencyKey);
    }
}
