package com.nanobaseai.actenora.security.microsoftconnection;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Durable retry queue for Graph mailbox calendar sync failures.
 */
public interface MailboxSyncWorkStore {

    /** Enqueues for immediate/near-term retry if not already pending. */
    void enqueue(UUID tenantId, String mailboxUserId, Instant now);

    List<WorkItem> claimDue(Instant now, int limit, Duration staleClaimAfter);

    void complete(UUID tenantId, String mailboxUserId, Instant now);

    void reschedule(
            UUID tenantId,
            String mailboxUserId,
            int attemptCount,
            Instant nextAttemptAt,
            String failureCode,
            Instant now);

    long countPending();

    record WorkItem(
            UUID tenantId,
            String mailboxUserId,
            int attemptCount,
            Instant createdAt
    ) {
    }
}
