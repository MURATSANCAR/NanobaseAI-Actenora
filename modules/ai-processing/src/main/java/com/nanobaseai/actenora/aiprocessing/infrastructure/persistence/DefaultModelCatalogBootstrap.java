package com.nanobaseai.actenora.aiprocessing.infrastructure.persistence;

import com.nanobaseai.actenora.aiprocessing.application.port.RoutableCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.DefaultModelRoleBootstrap;

import java.util.Set;

/**
 * Seeds FAZ 12 {@link InMemoryModelCatalog} when the model registry is empty.
 */
public final class DefaultModelCatalogBootstrap {

    private DefaultModelCatalogBootstrap() {
    }

    public static void seed(InMemoryModelCatalog catalog) {
        catalog.add(candidate(
                DefaultModelRoleBootstrap.FAST_EXTRACTION_MODEL_ID,
                DefaultModelRoleBootstrap.FAST_EXTRACTION_MODEL_KEY,
                DefaultModelRoleBootstrap.FAST_EXTRACTION_PRIMARY_DEPLOYMENT_ID,
                "fast-extraction-primary",
                Set.of(AiCapability.TRANSCRIPT_EXTRACTION),
                0.72,
                0.85,
                10
        ));
        catalog.add(candidate(
                DefaultModelRoleBootstrap.FAST_EXTRACTION_MODEL_ID,
                DefaultModelRoleBootstrap.FAST_EXTRACTION_MODEL_KEY,
                DefaultModelRoleBootstrap.FAST_EXTRACTION_SECONDARY_DEPLOYMENT_ID,
                "fast-extraction-secondary",
                Set.of(AiCapability.TRANSCRIPT_EXTRACTION),
                0.72,
                0.85,
                20
        ));
        catalog.add(candidate(
                DefaultModelRoleBootstrap.QWEN27_FINAL_MODEL_ID,
                DefaultModelRoleBootstrap.QWEN27_FINAL_MODEL_KEY,
                DefaultModelRoleBootstrap.QWEN27_PRIMARY_DEPLOYMENT_ID,
                "qwen27-final-primary",
                Set.of(
                        AiCapability.FINAL_NOTE,
                        AiCapability.SUMMARIZATION,
                        AiCapability.DECISION_EXTRACTION,
                        AiCapability.ACTION_EXTRACTION,
                        AiCapability.RISK_EXTRACTION
                ),
                0.92,
                0.6,
                10
        ));
        catalog.add(candidate(
                DefaultModelRoleBootstrap.QWEN27_FINAL_MODEL_ID,
                DefaultModelRoleBootstrap.QWEN27_FINAL_MODEL_KEY,
                DefaultModelRoleBootstrap.QWEN27_SECONDARY_DEPLOYMENT_ID,
                "qwen27-final-secondary",
                Set.of(
                        AiCapability.FINAL_NOTE,
                        AiCapability.SUMMARIZATION,
                        AiCapability.DECISION_EXTRACTION,
                        AiCapability.ACTION_EXTRACTION,
                        AiCapability.RISK_EXTRACTION
                ),
                0.92,
                0.6,
                20
        ));
        catalog.add(candidate(
                DefaultModelRoleBootstrap.VALIDATION_MODEL_ID,
                DefaultModelRoleBootstrap.VALIDATION_MODEL_KEY,
                DefaultModelRoleBootstrap.VALIDATION_DEPLOYMENT_ID,
                "validation-primary",
                Set.of(AiCapability.VALIDATION),
                0.88,
                0.7,
                10
        ));
    }

    private static RoutableCandidate candidate(
            java.util.UUID modelId,
            String modelKey,
            java.util.UUID deploymentId,
            String deploymentKey,
            Set<AiCapability> capabilities,
            double quality,
            double speed,
            int priority
    ) {
        return new RoutableCandidate(
                modelId,
                modelKey,
                deploymentId,
                deploymentKey,
                capabilities,
                8192,
                0,
                Set.of("en", "tr"),
                true,
                true,
                true,
                4,
                0,
                0,
                quality,
                speed,
                priority
        );
    }
}
