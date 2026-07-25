package com.nanobaseai.actenora.aiprocessing.domain.routing;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One inference attempt bound to a routing decision (attempt history entry).
 */
public record AttemptRecord(
        UUID attemptId,
        UUID jobId,
        UUID routingDecisionId,
        int attemptNumber,
        UUID modelDefinitionId,
        UUID deploymentId,
        String modelKey,
        ModelRole role,
        FallbackStep fallbackStep,
        AttemptStatus status,
        boolean qualityDowngraded,
        Optional<String> failureCategory,
        Optional<String> failureDetailSafe,
        Instant startedAt,
        Optional<Instant> completedAt
) {
    public AttemptRecord {
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(routingDecisionId, "routingDecisionId");
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be >= 1");
        }
        Objects.requireNonNull(modelDefinitionId, "modelDefinitionId");
        Objects.requireNonNull(deploymentId, "deploymentId");
        Objects.requireNonNull(modelKey, "modelKey");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(fallbackStep, "fallbackStep");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(failureCategory, "failureCategory");
        Objects.requireNonNull(failureDetailSafe, "failureDetailSafe");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(completedAt, "completedAt");
    }

    public enum AttemptStatus {
        STARTED,
        SUCCEEDED,
        FAILED,
        CANCELLED
    }
}
