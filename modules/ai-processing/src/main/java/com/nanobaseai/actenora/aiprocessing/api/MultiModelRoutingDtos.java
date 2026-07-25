package com.nanobaseai.actenora.aiprocessing.api;

import com.nanobaseai.actenora.aiprocessing.domain.routing.ConsensusMode;
import com.nanobaseai.actenora.aiprocessing.domain.routing.FallbackStep;
import com.nanobaseai.actenora.aiprocessing.domain.routing.InferenceTaskType;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ModelRole;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ValidationModelPreference;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Public DTOs for multi-model routing (FAZ 15). Prefer records; never expose JPA entities.
 */
public final class MultiModelRoutingDtos {

    private MultiModelRoutingDtos() {
    }

    public record RouteJobCommand(
            UUID jobId,
            UUID tenantId,
            InferenceTaskType taskType,
            boolean critical,
            UUID correlationId,
            Set<String> approvedAlternateModelKeys,
            boolean allowQualityDowngrade,
            boolean criticalJobsForbidDowngrade,
            ValidationModelPreference validationModelPreference,
            boolean shadowExecutionEnabled
    ) {
    }

    public record RoutingDecisionView(
            UUID decisionId,
            UUID jobId,
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
            Instant decidedAt,
            List<CandidateView> candidates
    ) {
    }

    public record CandidateView(
            UUID deploymentId,
            String modelKey,
            ModelRole role,
            FallbackStep step,
            boolean selected,
            String rejectReason
    ) {
    }

    public record ProvenanceView(
            UUID provenanceId,
            UUID jobId,
            UUID routingDecisionId,
            Optional<String> fromModelKey,
            Optional<String> toModelKey,
            FallbackStep fallbackStep,
            boolean qualityDowngraded,
            String reason,
            Instant recordedAt
    ) {
    }

    public record ShadowExecutionView(
            UUID shadowId,
            UUID jobId,
            UUID championDeploymentId,
            UUID challengerDeploymentId,
            String status,
            Optional<String> challengerResultRef,
            ConsensusMode consensusMode
    ) {
    }

    public record ModelQualityMetricsView(
            UUID modelDefinitionId,
            String modelKey,
            ModelRole role,
            long successCount,
            long failureCount,
            double averageLatencyMs,
            double schemaPassRate
    ) {
    }
}
