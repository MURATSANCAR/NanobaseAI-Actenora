package com.nanobaseai.actenora.security.microsoftconnection;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptPollWorkStoreTest {

    @Test
    void retrySurvivesClaimCycleAndCompletesIdempotently() {
        TranscriptPollWorkStore store = new InMemoryTranscriptPollWorkStore();
        UUID tenantId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-26T08:00:00Z");

        store.enqueue(tenantId, meetingId, now);
        store.enqueue(tenantId, meetingId, now);
        var first = store.claimDue(now, 10, Duration.ofMinutes(15));
        assertEquals(1, first.size());

        store.reschedule(tenantId, meetingId, 1, now.plusSeconds(60), "NOT_READY", now);
        assertTrue(store.claimDue(now.plusSeconds(30), 10, Duration.ofMinutes(15)).isEmpty());

        var retry = store.claimDue(now.plusSeconds(60), 10, Duration.ofMinutes(15));
        assertEquals(1, retry.size());
        assertEquals(1, retry.getFirst().attemptCount());

        store.complete(tenantId, meetingId, now.plusSeconds(60));
        store.enqueue(tenantId, meetingId, now.plusSeconds(120));
        assertEquals(0, store.countPending());
    }

    @Test
    void staleProcessingClaimCanBeRecovered() {
        TranscriptPollWorkStore store = new InMemoryTranscriptPollWorkStore();
        UUID tenantId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-26T08:00:00Z");
        store.enqueue(tenantId, meetingId, now);
        store.claimDue(now, 1, Duration.ofMinutes(15));

        assertEquals(1, store.claimDue(
                now.plus(Duration.ofMinutes(16)),
                1,
                Duration.ofMinutes(15)).size());
    }

    @Test
    void onlyDeadLetteredWorkCanBeOperatorRequeued() {
        TranscriptPollWorkStore store = new InMemoryTranscriptPollWorkStore();
        UUID tenantId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-26T08:00:00Z");
        store.enqueue(tenantId, meetingId, now);
        store.deadLetter(tenantId, meetingId, 24, "CONFIGURATION", now);

        assertTrue(store.requeueDeadLetter(tenantId, meetingId, now.plusSeconds(1)));
        assertEquals(1, store.countPending());
    }
}
