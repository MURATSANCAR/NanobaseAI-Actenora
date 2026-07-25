package com.nanobaseai.actenora.aiprocessing.domain.routing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Ordered attempt history for a job.
 */
public final class AttemptHistory {

    private final UUID jobId;
    private final List<AttemptRecord> attempts;

    public AttemptHistory(UUID jobId) {
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.attempts = new ArrayList<>();
    }

    public UUID jobId() {
        return jobId;
    }

    public List<AttemptRecord> attempts() {
        return List.copyOf(attempts);
    }

    public int nextAttemptNumber() {
        return attempts.size() + 1;
    }

    public void append(AttemptRecord attempt) {
        Objects.requireNonNull(attempt, "attempt");
        if (!jobId.equals(attempt.jobId())) {
            throw new IllegalArgumentException("attempt jobId mismatch");
        }
        if (attempt.attemptNumber() != nextAttemptNumber()) {
            throw new IllegalArgumentException(
                    "expected attemptNumber " + nextAttemptNumber() + " but got " + attempt.attemptNumber());
        }
        attempts.add(attempt);
    }

    public Instant firstStartedAt() {
        if (attempts.isEmpty()) {
            throw new IllegalStateException("no attempts");
        }
        return attempts.getFirst().startedAt();
    }

    public void replace(AttemptRecord completed) {
        Objects.requireNonNull(completed, "completed");
        for (int i = 0; i < attempts.size(); i++) {
            if (attempts.get(i).attemptId().equals(completed.attemptId())) {
                attempts.set(i, completed);
                return;
            }
        }
        throw new IllegalArgumentException("unknown attempt " + completed.attemptId());
    }
}
