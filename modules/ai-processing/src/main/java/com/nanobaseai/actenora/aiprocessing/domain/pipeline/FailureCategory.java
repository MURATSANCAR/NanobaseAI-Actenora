package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

/**
 * Classifies pipeline failures for retry vs permanent stop.
 */
public enum FailureCategory {
    INVALID_JSON,
    SCHEMA_VIOLATION,
    EVIDENCE_MISSING,
    HALLUCINATED_OWNER,
    HALLUCINATED_DATE,
    DUPLICATE_DECISION,
    PROMPT_INJECTION,
    CONTEXT_OVERFLOW,
    MODEL_UNAVAILABLE,
    LOW_CONFIDENCE,
    UNKNOWN;

    public boolean isRetryableOnce() {
        return this == INVALID_JSON || this == MODEL_UNAVAILABLE;
    }
}
