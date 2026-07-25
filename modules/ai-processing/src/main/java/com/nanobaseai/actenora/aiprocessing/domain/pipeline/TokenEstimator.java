package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

/**
 * Approximate token counting for context budgeting (not a model tokenizer).
 */
public interface TokenEstimator {

    int estimate(String text);

    static TokenEstimator approximate() {
        return new ApproximateTokenEstimator();
    }
}
