package com.nanobaseai.actenora.aiprocessing.domain.routing;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One candidate evaluated during routing, retained for audit.
 */
public record CandidateEvaluation(
        UUID deploymentId,
        UUID modelDefinitionId,
        String modelKey,
        ModelRole role,
        FallbackStep consideredForStep,
        boolean selected,
        String rejectReason
) {
    public CandidateEvaluation {
        Objects.requireNonNull(deploymentId, "deploymentId");
        Objects.requireNonNull(modelDefinitionId, "modelDefinitionId");
        Objects.requireNonNull(modelKey, "modelKey");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(consideredForStep, "consideredForStep");
        Objects.requireNonNull(rejectReason, "rejectReason");
    }

    public static CandidateEvaluation rejected(
            LocalDeploymentRef deployment,
            FallbackStep step,
            String reason
    ) {
        return new CandidateEvaluation(
                deployment.deploymentId(),
                deployment.modelDefinitionId(),
                deployment.modelKey(),
                deployment.role(),
                step,
                false,
                reason
        );
    }

    public static CandidateEvaluation selected(LocalDeploymentRef deployment, FallbackStep step) {
        return new CandidateEvaluation(
                deployment.deploymentId(),
                deployment.modelDefinitionId(),
                deployment.modelKey(),
                deployment.role(),
                step,
                true,
                ""
        );
    }

    public Optional<String> rejectReasonOptional() {
        return rejectReason.isBlank() ? Optional.empty() : Optional.of(rejectReason);
    }
}
