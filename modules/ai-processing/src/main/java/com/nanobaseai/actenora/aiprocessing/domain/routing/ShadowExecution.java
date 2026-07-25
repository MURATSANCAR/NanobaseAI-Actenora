package com.nanobaseai.actenora.aiprocessing.domain.routing;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Champion/challenger shadow execution. Challenger output must never replace production.
 */
public record ShadowExecution(
        UUID shadowId,
        UUID jobId,
        UUID routingDecisionId,
        UUID championDeploymentId,
        UUID challengerDeploymentId,
        UUID championModelDefinitionId,
        UUID challengerModelDefinitionId,
        ShadowStatus status,
        Optional<String> challengerResultRef,
        Optional<String> comparisonSummarySafe,
        Instant createdAt,
        Optional<Instant> completedAt
) {
    public ShadowExecution {
        Objects.requireNonNull(shadowId, "shadowId");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(routingDecisionId, "routingDecisionId");
        Objects.requireNonNull(championDeploymentId, "championDeploymentId");
        Objects.requireNonNull(challengerDeploymentId, "challengerDeploymentId");
        Objects.requireNonNull(championModelDefinitionId, "championModelDefinitionId");
        Objects.requireNonNull(challengerModelDefinitionId, "challengerModelDefinitionId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(challengerResultRef, "challengerResultRef");
        Objects.requireNonNull(comparisonSummarySafe, "comparisonSummarySafe");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(completedAt, "completedAt");
        if (championDeploymentId.equals(challengerDeploymentId)) {
            throw new IllegalArgumentException("champion and challenger deployments must differ");
        }
    }

    public enum ShadowStatus {
        SCHEDULED,
        RUNNING,
        COMPLETED,
        FAILED
    }

    public ShadowExecution withChallengerResult(String resultRef, String comparisonSummarySafe, Instant completedAt) {
        return new ShadowExecution(
                shadowId,
                jobId,
                routingDecisionId,
                championDeploymentId,
                challengerDeploymentId,
                championModelDefinitionId,
                challengerModelDefinitionId,
                ShadowStatus.COMPLETED,
                Optional.of(resultRef),
                Optional.ofNullable(comparisonSummarySafe),
                createdAt,
                Optional.of(completedAt)
        );
    }
}
