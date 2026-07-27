package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FailureCategory;

/**
 * Emits pipeline quality counters without coupling AI module to Micrometer/OTel.
 */
public interface PipelineQualityMetricsPort {

    void recordFailure(FailureCategory category);

    static PipelineQualityMetricsPort noop() {
        return category -> {
            // intentionally empty
        };
    }
}
