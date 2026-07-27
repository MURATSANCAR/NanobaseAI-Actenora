package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

/**
 * Optional sink so platform can bridge gate counters to Micrometer without
 * coupling ai-processing to observability.
 */
public interface ChunkGateMetricListener {

    ChunkGateMetricListener NOOP = new ChunkGateMetricListener() {
    };

    default void onTotal(String policyVersion) {
    }

    default void onSkipped(String policyVersion, int tokensSaved) {
    }

    default void onExtracted(String policyVersion) {
    }

    default void onContinuation(String policyVersion) {
    }

    default void onClassifierExtract(String policyVersion) {
    }

    default void onShadowFalseNegative(String policyVersion) {
    }

    default void onDecisionUnsupported(String policyVersion) {
    }
}
