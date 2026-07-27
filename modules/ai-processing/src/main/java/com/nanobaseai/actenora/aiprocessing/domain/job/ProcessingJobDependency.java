package com.nanobaseai.actenora.aiprocessing.domain.job;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Edge in the staged pipeline DAG: {@code jobId} waits until {@code dependsOnJobId} succeeds.
 */
public final class ProcessingJobDependency {

    public enum Status {
        PENDING,
        SATISFIED,
        CANCELLED
    }

    private final UUID jobId;
    private final UUID dependsOnJobId;
    private Status status;
    private final Instant createdAt;

    public ProcessingJobDependency(UUID jobId, UUID dependsOnJobId, Status status, Instant createdAt) {
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.dependsOnJobId = Objects.requireNonNull(dependsOnJobId, "dependsOnJobId");
        if (jobId.equals(dependsOnJobId)) {
            throw new IllegalArgumentException("job cannot depend on itself");
        }
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static ProcessingJobDependency pending(UUID jobId, UUID dependsOnJobId, Instant createdAt) {
        return new ProcessingJobDependency(jobId, dependsOnJobId, Status.PENDING, createdAt);
    }

    public void markSatisfied() {
        if (status == Status.CANCELLED) {
            throw AiJobException.invalidTransition("Cannot satisfy cancelled dependency");
        }
        this.status = Status.SATISFIED;
    }

    public void cancel() {
        this.status = Status.CANCELLED;
    }

    public UUID jobId() {
        return jobId;
    }

    public UUID dependsOnJobId() {
        return dependsOnJobId;
    }

    public Status status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
