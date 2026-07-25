package com.nanobaseai.actenora.aiprocessing.domain.routing;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Provenance when the selected model/deployment changes along the fallback chain.
 */
public record ModelChangeProvenance(
        UUID provenanceId,
        UUID jobId,
        UUID routingDecisionId,
        Optional<UUID> fromModelDefinitionId,
        Optional<UUID> fromDeploymentId,
        Optional<String> fromModelKey,
        Optional<UUID> toModelDefinitionId,
        Optional<UUID> toDeploymentId,
        Optional<String> toModelKey,
        FallbackStep fallbackStep,
        boolean qualityDowngraded,
        String reason,
        Instant recordedAt
) {
    public ModelChangeProvenance {
        Objects.requireNonNull(provenanceId, "provenanceId");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(routingDecisionId, "routingDecisionId");
        Objects.requireNonNull(fromModelDefinitionId, "fromModelDefinitionId");
        Objects.requireNonNull(fromDeploymentId, "fromDeploymentId");
        Objects.requireNonNull(fromModelKey, "fromModelKey");
        Objects.requireNonNull(toModelDefinitionId, "toModelDefinitionId");
        Objects.requireNonNull(toDeploymentId, "toDeploymentId");
        Objects.requireNonNull(toModelKey, "toModelKey");
        Objects.requireNonNull(fallbackStep, "fallbackStep");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(recordedAt, "recordedAt");
    }
}
