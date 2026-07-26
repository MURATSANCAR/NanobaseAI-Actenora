package com.nanobaseai.actenora.aiprocessing.application.execution;

import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderFailureCategory;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FailureCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PipelineFailureMapperTest {

    @Test
    void mapsRetryableAndPermanentCategories() {
        assertEquals(
                ProviderFailureCategory.HEALTH_DEGRADED,
                PipelineFailureMapper.toProviderCategory(FailureCategory.MODEL_UNAVAILABLE));
        assertEquals(
                ProviderFailureCategory.MALFORMED_RESPONSE,
                PipelineFailureMapper.toProviderCategory(FailureCategory.INVALID_JSON));
        assertEquals(
                ProviderFailureCategory.UNKNOWN,
                PipelineFailureMapper.toProviderCategory(FailureCategory.EVIDENCE_MISSING));
        assertEquals(
                ProviderFailureCategory.PROVIDER_ERROR,
                PipelineFailureMapper.toProviderCategory(FailureCategory.CONTEXT_OVERFLOW));
    }
}
