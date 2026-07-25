package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

/**
 * Outcome of classifying a failure for another attempt.
 */
public enum RetryDecision {
    RETRY,
    PERMANENT_FAILURE
}
