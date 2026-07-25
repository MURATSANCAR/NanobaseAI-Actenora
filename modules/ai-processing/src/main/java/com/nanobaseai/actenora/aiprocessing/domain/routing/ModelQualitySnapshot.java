package com.nanobaseai.actenora.aiprocessing.domain.routing;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable quality metrics view for a model.
 */
public record ModelQualitySnapshot(
        UUID modelDefinitionId,
        String modelKey,
        ModelRole role,
        long successCount,
        long failureCount,
        double averageLatencyMs,
        double schemaPassRate
) {
    public ModelQualitySnapshot {
        Objects.requireNonNull(modelDefinitionId, "modelDefinitionId");
        Objects.requireNonNull(modelKey, "modelKey");
        Objects.requireNonNull(role, "role");
    }
}
