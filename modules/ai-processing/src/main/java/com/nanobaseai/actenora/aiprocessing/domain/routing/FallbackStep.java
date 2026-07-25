package com.nanobaseai.actenora.aiprocessing.domain.routing;

/**
 * Ordered local fallback chain steps.
 * <pre>
 * PRIMARY → SAME_MODEL_OTHER_DEPLOYMENT → ALTERNATE_LOCAL_MODEL → RETRY_QUEUE → MANUAL_REVIEW
 * </pre>
 */
public enum FallbackStep {
    PRIMARY,
    SAME_MODEL_OTHER_DEPLOYMENT,
    ALTERNATE_LOCAL_MODEL,
    RETRY_QUEUE,
    MANUAL_REVIEW
}
