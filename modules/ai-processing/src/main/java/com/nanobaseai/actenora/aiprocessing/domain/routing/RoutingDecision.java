package com.nanobaseai.actenora.aiprocessing.domain.routing;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable routing decision audit record.
 */
public record RoutingDecision(
        UUID decisionId,
        UUID jobId,
        UUID tenantId,
        UUID correlationId,
        InferenceTaskType taskType,
        ModelRole requestedRole,
        FallbackStep fallbackStep,
        Optional<UUID> selectedModelDefinitionId,
        Optional<UUID> selectedDeploymentId,
        Optional<String> selectedModelKey,
        boolean qualityDowngraded,
        boolean requiresRetryQueue,
        boolean requiresManualReview,
        String reason,
        List<CandidateEvaluation> candidatesConsidered,
        Instant decidedAt
) {
    public RoutingDecision {
        Objects.requireNonNull(decisionId, "decisionId");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(taskType, "taskType");
        Objects.requireNonNull(requestedRole, "requestedRole");
        Objects.requireNonNull(fallbackStep, "fallbackStep");
        Objects.requireNonNull(selectedModelDefinitionId, "selectedModelDefinitionId");
        Objects.requireNonNull(selectedDeploymentId, "selectedDeploymentId");
        Objects.requireNonNull(selectedModelKey, "selectedModelKey");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(candidatesConsidered, "candidatesConsidered");
        candidatesConsidered = List.copyOf(candidatesConsidered);
        Objects.requireNonNull(decidedAt, "decidedAt");
    }

    public boolean hasProductionRoute() {
        return selectedDeploymentId.isPresent() && !requiresRetryQueue && !requiresManualReview;
    }
}
