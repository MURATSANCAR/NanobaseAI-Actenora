package com.nanobaseai.actenora.modelmanagement.application;

import com.nanobaseai.actenora.modelmanagement.domain.DeploymentStatus;
import com.nanobaseai.actenora.modelmanagement.domain.ModelStatus;

import java.time.Instant;
import java.util.List;

/**
 * Aggregated health view for ops (FAZ 11 health view API).
 */
public record ModelHealthView(
        Instant generatedAt,
        List<ModelHealthEntry> models
) {
    public record ModelHealthEntry(
            String modelKey,
            ModelStatus status,
            boolean acceptingNewWork,
            int healthyDeployments,
            int drainingDeployments,
            int unhealthyDeployments,
            List<DeploymentHealthEntry> deployments
    ) {
    }

    public record DeploymentHealthEntry(
            String deploymentKey,
            DeploymentStatus status,
            boolean acceptingNewWork,
            boolean heartbeatTimedOut,
            Instant lastHeartbeatAt
    ) {
    }
}
