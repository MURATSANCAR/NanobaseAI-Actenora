package com.nanobaseai.actenora.aiprocessing.infrastructure.routing;

import com.nanobaseai.actenora.aiprocessing.domain.routing.LocalDeploymentRef;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ModelRole;

import java.util.UUID;

/**
 * Seeds the two starting local model roles. When a real FastExtraction model is absent,
 * a mock deployment is registered so routing/orchestration still run end-to-end.
 */
public final class DefaultModelRoleBootstrap {

    public static final String FAST_EXTRACTION_MODEL_KEY = "local.fast-extraction";
    public static final String QWEN27_FINAL_MODEL_KEY = "local.qwen27-final";
    public static final String VALIDATION_MODEL_KEY = "local.validation";

    public static final UUID FAST_EXTRACTION_MODEL_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111101");
    public static final UUID QWEN27_FINAL_MODEL_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111102");
    public static final UUID VALIDATION_MODEL_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111103");

    public static final UUID FAST_EXTRACTION_PRIMARY_DEPLOYMENT_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222201");
    public static final UUID FAST_EXTRACTION_SECONDARY_DEPLOYMENT_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222202");
    public static final UUID QWEN27_PRIMARY_DEPLOYMENT_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222203");
    public static final UUID QWEN27_SECONDARY_DEPLOYMENT_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222204");
    public static final UUID VALIDATION_DEPLOYMENT_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222205");

    private DefaultModelRoleBootstrap() {
    }

    /**
     * @param includeRealFastExtraction when false, FastExtraction is registered as a mock deployment
     */
    public static void seed(InMemoryLocalDeploymentCatalog catalog, boolean includeRealFastExtraction) {
        catalog.upsert(new LocalDeploymentRef(
                FAST_EXTRACTION_PRIMARY_DEPLOYMENT_ID,
                FAST_EXTRACTION_MODEL_ID,
                FAST_EXTRACTION_MODEL_KEY,
                includeRealFastExtraction ? "fast-extraction-primary" : "fast-extraction-mock",
                ModelRole.FAST_EXTRACTION,
                0.72,
                true,
                !includeRealFastExtraction,
                10));

        catalog.upsert(new LocalDeploymentRef(
                FAST_EXTRACTION_SECONDARY_DEPLOYMENT_ID,
                FAST_EXTRACTION_MODEL_ID,
                FAST_EXTRACTION_MODEL_KEY,
                "fast-extraction-secondary",
                ModelRole.FAST_EXTRACTION,
                0.72,
                true,
                !includeRealFastExtraction,
                20));

        catalog.upsert(new LocalDeploymentRef(
                QWEN27_PRIMARY_DEPLOYMENT_ID,
                QWEN27_FINAL_MODEL_ID,
                QWEN27_FINAL_MODEL_KEY,
                "qwen27-final-primary",
                ModelRole.PRIMARY_QUALITY,
                0.92,
                true,
                false,
                10));

        catalog.upsert(new LocalDeploymentRef(
                QWEN27_SECONDARY_DEPLOYMENT_ID,
                QWEN27_FINAL_MODEL_ID,
                QWEN27_FINAL_MODEL_KEY,
                "qwen27-final-secondary",
                ModelRole.PRIMARY_QUALITY,
                0.92,
                true,
                false,
                20));

        catalog.upsert(new LocalDeploymentRef(
                VALIDATION_DEPLOYMENT_ID,
                VALIDATION_MODEL_ID,
                VALIDATION_MODEL_KEY,
                "validation-primary",
                ModelRole.VALIDATION,
                0.88,
                true,
                false,
                10));
    }
}
