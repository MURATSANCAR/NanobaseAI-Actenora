package com.nanobaseai.actenora.aiprocessing.application.pipeline.staged;

import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingStage;

/**
 * Stage-level metrics for OTel adapters in the composition root.
 */
public interface StageMetricsPort {

    void recordDuration(ProcessingStage stage, long durationMs, boolean success);

    void recordQueueWait(ProcessingStage stage, long waitMs);

    void recordDlq(ProcessingStage stage);

    void recordEarlyExit();

    static StageMetricsPort noop() {
        return new StageMetricsPort() {
            @Override
            public void recordDuration(ProcessingStage stage, long durationMs, boolean success) {
            }

            @Override
            public void recordQueueWait(ProcessingStage stage, long waitMs) {
            }

            @Override
            public void recordDlq(ProcessingStage stage) {
            }

            @Override
            public void recordEarlyExit() {
            }
        };
    }
}
