package com.nanobaseai.actenora.sharedkernel.coordination;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Short-lived meeting processing progress cache. Durable job state remains in PostgreSQL.
 */
public interface JobProgressCache {

    void put(UUID meetingOccurrenceId, Progress progress);

    Optional<Progress> get(UUID meetingOccurrenceId);

    record Progress(
            UUID jobId,
            String status,
            String stage,
            int attemptCount,
            Instant updatedAt
    ) {
        public Progress {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(stage, "stage");
            if (attemptCount < 0) {
                throw new IllegalArgumentException("attemptCount must be >= 0");
            }
            Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }
}
