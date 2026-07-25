package com.nanobaseai.actenora.sharedkernel.messaging;

/**
 * Classifies consumer/publisher failures into retryable vs poison (dead-letter).
 */
public enum RetryClassification {
    /** Transient failure — schedule retry with backoff. */
    TRANSIENT,
    /** Permanent / poison — send to DLQ, do not retry indefinitely. */
    POISON,
    /** Schema / version / payload policy violation — reject without retry. */
    REJECT
}
