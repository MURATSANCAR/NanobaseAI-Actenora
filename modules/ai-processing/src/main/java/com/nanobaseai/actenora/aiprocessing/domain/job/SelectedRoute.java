package com.nanobaseai.actenora.aiprocessing.domain.job;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Persisted routing choice and human-readable reason for audit.
 */
public record SelectedRoute(
        UUID modelDefinitionId,
        UUID deploymentId,
        String modelKey,
        String reason,
        List<String> rejectReasons,
        Instant selectedAt
) {
    public SelectedRoute {
        Objects.requireNonNull(modelDefinitionId, "modelDefinitionId");
        Objects.requireNonNull(deploymentId, "deploymentId");
        Objects.requireNonNull(modelKey, "modelKey");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(rejectReasons, "rejectReasons");
        rejectReasons = List.copyOf(rejectReasons);
        Objects.requireNonNull(selectedAt, "selectedAt");
    }
}
