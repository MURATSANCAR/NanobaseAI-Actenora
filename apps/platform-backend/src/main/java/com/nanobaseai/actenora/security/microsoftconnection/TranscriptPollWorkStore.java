package com.nanobaseai.actenora.security.microsoftconnection;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TranscriptPollWorkStore {

    void enqueue(UUID tenantId, UUID meetingOccurrenceId, Instant now);

    List<WorkItem> claimDue(Instant now, int limit, Duration staleClaimAfter);

    void complete(UUID tenantId, UUID meetingOccurrenceId, Instant now);

    void reschedule(
            UUID tenantId,
            UUID meetingOccurrenceId,
            int attemptCount,
            Instant nextAttemptAt,
            String failureCode,
            Instant now);

    void deadLetter(
            UUID tenantId,
            UUID meetingOccurrenceId,
            int attemptCount,
            String failureCode,
            Instant now);

    boolean requeueDeadLetter(UUID tenantId, UUID meetingOccurrenceId, Instant now);

    long countPending();

    Optional<Instant> oldestPendingCreatedAt();

    record WorkItem(
            UUID tenantId,
            UUID meetingOccurrenceId,
            int attemptCount,
            Instant createdAt
    ) {
    }
}
