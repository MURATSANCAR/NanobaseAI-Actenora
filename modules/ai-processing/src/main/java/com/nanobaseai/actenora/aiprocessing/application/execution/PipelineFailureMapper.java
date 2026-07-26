package com.nanobaseai.actenora.aiprocessing.application.execution;

import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderFailureCategory;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FailureCategory;

/**
 * Maps FAZ 14 pipeline failure taxonomy onto the FAZ 13 provider attempt taxonomy.
 */
public final class PipelineFailureMapper {

    private PipelineFailureMapper() {
    }

    public static ProviderFailureCategory toProviderCategory(FailureCategory category) {
        if (category == null) {
            return ProviderFailureCategory.UNKNOWN;
        }
        return switch (category) {
            case MODEL_UNAVAILABLE -> ProviderFailureCategory.HEALTH_DEGRADED;
            case INVALID_JSON, SCHEMA_VIOLATION -> ProviderFailureCategory.MALFORMED_RESPONSE;
            case CONTEXT_OVERFLOW -> ProviderFailureCategory.PROVIDER_ERROR;
            case EVIDENCE_MISSING,
                 HALLUCINATED_OWNER,
                 HALLUCINATED_DATE,
                 DUPLICATE_DECISION,
                 PROMPT_INJECTION,
                 LOW_CONFIDENCE,
                 UNKNOWN -> ProviderFailureCategory.UNKNOWN;
        };
    }
}
