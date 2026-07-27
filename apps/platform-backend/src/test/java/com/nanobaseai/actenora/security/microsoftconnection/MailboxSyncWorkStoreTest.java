package com.nanobaseai.actenora.security.microsoftconnection;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailboxSyncWorkStoreTest {

    @Test
    void enqueueClaimRescheduleAndComplete() {
        MailboxSyncWorkStore store = new InMemoryMailboxSyncWorkStore();
        UUID tenantId = UUID.randomUUID();
        String mailbox = "user-1";
        Instant now = Instant.parse("2026-07-27T08:00:00Z");

        store.enqueue(tenantId, mailbox, now);
        store.enqueue(tenantId, mailbox, now);
        assertEquals(1, store.claimDue(now, 10, Duration.ofMinutes(5)).size());

        store.reschedule(tenantId, mailbox, 1, now.plusSeconds(60), "GraphDown", now);
        assertTrue(store.claimDue(now.plusSeconds(30), 10, Duration.ofMinutes(5)).isEmpty());

        var retry = store.claimDue(now.plusSeconds(60), 10, Duration.ofMinutes(5));
        assertEquals(1, retry.size());
        assertEquals(1, retry.getFirst().attemptCount());

        store.complete(tenantId, mailbox, now.plusSeconds(60));
        assertEquals(0, store.countPending());
    }

    @Test
    void enqueueReactivatesCompletedWork() {
        MailboxSyncWorkStore store = new InMemoryMailboxSyncWorkStore();
        UUID tenantId = UUID.randomUUID();
        String mailbox = "user-1";
        Instant now = Instant.parse("2026-07-27T08:00:00Z");

        store.enqueue(tenantId, mailbox, now);
        store.complete(tenantId, mailbox, now);
        assertEquals(0, store.countPending());

        store.enqueue(tenantId, mailbox, now.plusSeconds(10));
        assertEquals(1, store.countPending());
    }

    @Test
    void staleProcessingClaimCanBeRecovered() {
        MailboxSyncWorkStore store = new InMemoryMailboxSyncWorkStore();
        UUID tenantId = UUID.randomUUID();
        String mailbox = "user-1";
        Instant now = Instant.parse("2026-07-27T08:00:00Z");
        store.enqueue(tenantId, mailbox, now);
        store.claimDue(now, 1, Duration.ofMinutes(5));

        assertEquals(1, store.claimDue(
                now.plus(Duration.ofMinutes(6)),
                1,
                Duration.ofMinutes(5)).size());
    }
}
