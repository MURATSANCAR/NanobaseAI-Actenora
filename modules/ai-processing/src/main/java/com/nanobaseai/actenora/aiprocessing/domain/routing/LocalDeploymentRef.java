package com.nanobaseai.actenora.aiprocessing.domain.routing;

import java.util.Objects;
import java.util.UUID;

/**
 * Local deployment snapshot used by the router (owned catalog projected via port).
 */
public record LocalDeploymentRef(
        UUID deploymentId,
        UUID modelDefinitionId,
        String modelKey,
        String deploymentKey,
        ModelRole role,
        double qualityScore,
        boolean healthy,
        boolean mock,
        int priority
) {
    public LocalDeploymentRef {
        Objects.requireNonNull(deploymentId, "deploymentId");
        Objects.requireNonNull(modelDefinitionId, "modelDefinitionId");
        Objects.requireNonNull(modelKey, "modelKey");
        Objects.requireNonNull(deploymentKey, "deploymentKey");
        Objects.requireNonNull(role, "role");
        if (qualityScore < 0.0 || qualityScore > 1.0) {
            throw new IllegalArgumentException("qualityScore must be in [0,1]");
        }
    }
}
