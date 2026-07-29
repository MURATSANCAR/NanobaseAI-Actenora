package com.nanobaseai.actenora.microsoftconnection.infrastructure.mail;

import com.nanobaseai.actenora.microsoftconnection.application.model.OutlookDraftRequest;
import com.nanobaseai.actenora.microsoftconnection.application.model.OutlookDraftResult;
import com.nanobaseai.actenora.microsoftconnection.application.port.OutlookDraftGateway;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.GraphApiException;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence.InMemoryOutlookDraftReceiptStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurableOutlookDraftGatewayTest {

    @Test
    void reusesCompletedReceiptAcrossGatewayInstances() {
        var receipts = new InMemoryOutlookDraftReceiptStore();
        AtomicInteger calls = new AtomicInteger();
        OutlookDraftGateway transport = (tenantId, request) -> {
            calls.incrementAndGet();
            return new OutlookDraftResult("draft-1", "https://outlook.example/draft-1", false);
        };
        UUID tenantId = UUID.randomUUID();
        OutlookDraftRequest request = request("durable-key");

        var firstGateway = new DurableOutlookDraftGateway(transport, receipts);
        OutlookDraftResult created = firstGateway.create(tenantId, request);
        var restartedGateway = new DurableOutlookDraftGateway(transport, receipts);
        OutlookDraftResult reused = restartedGateway.create(tenantId, request);

        assertEquals(created.providerMessageId(), reused.providerMessageId());
        assertTrue(reused.reused());
        assertEquals(1, calls.get());
    }

    @Test
    void retainsClaimWhenGraphOutcomeIsAmbiguous() {
        var receipts = new InMemoryOutlookDraftReceiptStore();
        AtomicInteger calls = new AtomicInteger();
        OutlookDraftGateway transport = (tenantId, request) -> {
            calls.incrementAndGet();
            throw GraphApiException.serverError(503, "ambiguous");
        };
        UUID tenantId = UUID.randomUUID();
        OutlookDraftRequest request = request("ambiguous-key");

        var gateway = new DurableOutlookDraftGateway(transport, receipts);
        assertThrows(GraphApiException.class, () -> gateway.create(tenantId, request));
        GraphApiException pending = assertThrows(
                GraphApiException.class,
                () -> new DurableOutlookDraftGateway(transport, receipts).create(tenantId, request));

        assertEquals("OUTLOOK_DRAFT_CREATION_PENDING", pending.code());
        assertEquals(1, calls.get());
    }

    @Test
    void releasesClaimAfterDefinitiveFailure() {
        var receipts = new InMemoryOutlookDraftReceiptStore();
        AtomicInteger calls = new AtomicInteger();
        OutlookDraftGateway transport = (tenantId, request) -> {
            if (calls.incrementAndGet() == 1) {
                throw GraphApiException.configuration("permission missing");
            }
            return new OutlookDraftResult("draft-2", null, false);
        };
        UUID tenantId = UUID.randomUUID();
        OutlookDraftRequest request = request("retryable-key");
        var gateway = new DurableOutlookDraftGateway(transport, receipts);

        assertThrows(GraphApiException.class, () -> gateway.create(tenantId, request));
        OutlookDraftResult created = gateway.create(tenantId, request);

        assertEquals("draft-2", created.providerMessageId());
        assertEquals(2, calls.get());
    }

    private static OutlookDraftRequest request(String key) {
        return new OutlookDraftRequest(
                "mailbox",
                "Subject",
                "<p>Body</p>",
                List.of("person@example.com"),
                key);
    }
}
