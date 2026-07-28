package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FailureCategory;

/**
 * Emits pipeline quality counters without coupling AI module to Micrometer/OTel.
 */
public interface PipelineQualityMetricsPort {

    void recordFailure(FailureCategory category);

    default void recordFallback(String stage, String reason) {
        // optional; adapters may override
    }

    static PipelineQualityMetricsPort noop() {
        return new PipelineQualityMetricsPort() {
            @Override
            public void recordFailure(FailureCategory category) {
                // intentionally empty
            }
        };
    }
}
