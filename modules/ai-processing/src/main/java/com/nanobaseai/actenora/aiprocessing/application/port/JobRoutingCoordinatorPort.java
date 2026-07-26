package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.routing.FallbackStep;
import com.nanobaseai.actenora.aiprocessing.domain.routing.InferenceTaskType;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * FAZ 15 binding: lets the execution path take a role-based routing decision at claim time and
 * report the outcome back into routing provenance, attempt history, and quality metrics.
 */
public interface JobRoutingCoordinatorPort {

    /**
     * Produces a routing decision for a claimed job. The returned execution may lack a production
     * route (retry queue / manual review), in which case the caller must not run inference.
     */
    RoutedExecution routeForExecution(AiJob job, InferenceTaskType taskType);

    void recordSuccess(RoutedExecution routed, long latencyMs, boolean schemaPassed);

    void recordFailure(RoutedExecution routed, long latencyMs, String failureCategory, String detailSafe);

    record RoutedExecution(
            UUID jobId,
            UUID decisionId,
            Optional<UUID> attemptId,
            Optional<UUID> modelDefinitionId,
            Optional<UUID> deploymentId,
            Optional<String> modelKey,
            FallbackStep fallbackStep,
            boolean qualityDowngraded,
            boolean requiresRetryQueue,
            boolean requiresManualReview,
            String reason
    ) {
        public RoutedExecution {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(decisionId, "decisionId");
            Objects.requireNonNull(attemptId, "attemptId");
            Objects.requireNonNull(modelDefinitionId, "modelDefinitionId");
            Objects.requireNonNull(deploymentId, "deploymentId");
            Objects.requireNonNull(modelKey, "modelKey");
            Objects.requireNonNull(fallbackStep, "fallbackStep");
            Objects.requireNonNull(reason, "reason");
        }

        public boolean hasProductionRoute() {
            return deploymentId.isPresent() && !requiresRetryQueue && !requiresManualReview;
        }
    }
}
