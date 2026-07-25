package com.nanobaseai.actenora.operations;

import com.nanobaseai.actenora.operations.api.OperationsApi;
import com.nanobaseai.actenora.operations.application.LegalHoldService;
import com.nanobaseai.actenora.operations.application.OperationsCenterService;
import com.nanobaseai.actenora.operations.application.RetentionJobService;
import com.nanobaseai.actenora.operations.domain.AlertThresholds;
import com.nanobaseai.actenora.operations.domain.retention.LegalHold;
import com.nanobaseai.actenora.operations.domain.retention.RetentionCandidate;
import com.nanobaseai.actenora.operations.domain.retention.RetentionResourceType;
import com.nanobaseai.actenora.operations.infrastructure.InMemoryOpsTelemetryPort;
import com.nanobaseai.actenora.operations.infrastructure.persistence.InMemoryLegalHoldRepository;
import com.nanobaseai.actenora.operations.infrastructure.retention.InMemoryRetentionSupport;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryDeadLetterStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryInboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryOutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.replay.EventReplayer;
import com.nanobaseai.actenora.sharedkernel.messaging.support.TenantFairnessTracker;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetentionAndLegalHoldTest {

    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");

    private InMemoryLegalHoldRepository holds;
    private InMemoryRetentionSupport retention;
    private OperationsApi operationsApi;
    private TenantId tenant;

    @BeforeEach
    void setUp() {
        holds = new InMemoryLegalHoldRepository();
        retention = new InMemoryRetentionSupport();
        InstantClock clock = new InstantClock(Clock.fixed(NOW, ZoneOffset.UTC));
        InMemoryOpsTelemetryPort telemetry = new InMemoryOpsTelemetryPort();
        InMemoryDeadLetterStore dlq = new InMemoryDeadLetterStore();
        InMemoryOutboxStore outbox = new InMemoryOutboxStore(new TenantFairnessTracker());
        EventReplayer replayer = new EventReplayer(outbox, new InMemoryInboxStore(), dlq, clock);
        OperationsCenterService opsCenter = new OperationsCenterService(
                telemetry, dlq, replayer, AlertThresholds.defaults(), clock);
        operationsApi = new OperationsApi(
                opsCenter,
                new RetentionJobService(retention, holds, retention, retention, clock),
                new LegalHoldService(holds, retention, clock));
        tenant = TenantId.random();
    }

    @Test
    void retentionDeletesExpiredTranscriptAndPrivateNote() {
        UUID transcriptId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        RetentionCandidate transcript = RetentionCandidate.transcript(
                tenant, transcriptId, NOW.minusSeconds(60), "tenants/" + tenant.value() + "/raw.vtt");
        RetentionCandidate note = RetentionCandidate.privateNote(tenant, noteId, NOW.minusSeconds(1));
        retention.addCandidate(transcript);
        retention.addCandidate(note);

        RetentionJobService.RetentionRunResult result = operationsApi.runRetentionJob();

        assertEquals(2, result.scanned());
        assertEquals(2, result.deleted());
        assertEquals(0, result.blockedByLegalHold());
        assertFalse(retention.stillPresent(transcript));
        assertFalse(retention.stillPresent(note));
        assertTrue(retention.auditEvents().stream().anyMatch(e -> e.startsWith("DELETED|")));
    }

    @Test
    void legalHoldBlocksRetentionDeletion() {
        UUID transcriptId = UUID.randomUUID();
        RetentionCandidate transcript = RetentionCandidate.transcript(
                tenant, transcriptId, NOW.minusSeconds(10), "key");
        retention.addCandidate(transcript);

        LegalHold hold = operationsApi.placeLegalHold(
                tenant,
                RetentionResourceType.TRANSCRIPT,
                transcriptId.toString(),
                "litigation hold",
                UUID.randomUUID(),
                true);
        assertTrue(hold.isActive());

        RetentionJobService.RetentionRunResult result = operationsApi.runRetentionJob();

        assertEquals(1, result.scanned());
        assertEquals(0, result.deleted());
        assertEquals(1, result.blockedByLegalHold());
        assertTrue(retention.stillPresent(transcript));
        assertTrue(retention.auditEvents().stream().anyMatch(e -> e.startsWith("BLOCKED|")));
    }

    @Test
    void releasingLegalHoldAllowsSubsequentDeletion() {
        UUID noteId = UUID.randomUUID();
        RetentionCandidate note = RetentionCandidate.privateNote(tenant, noteId, NOW.minusSeconds(5));
        retention.addCandidate(note);
        LegalHold hold = operationsApi.placeLegalHold(
                tenant, RetentionResourceType.PRIVATE_NOTE, noteId.toString(), "investigation", null, true);

        assertEquals(0, operationsApi.runRetentionJob().deleted());

        operationsApi.releaseLegalHold(hold.id());
        RetentionJobService.RetentionRunResult after = operationsApi.runRetentionJob();
        assertEquals(1, after.deleted());
        assertFalse(retention.stillPresent(note));
    }
}
