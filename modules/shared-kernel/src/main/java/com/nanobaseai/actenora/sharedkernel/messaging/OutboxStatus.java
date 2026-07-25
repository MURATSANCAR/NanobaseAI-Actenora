package com.nanobaseai.actenora.sharedkernel.messaging;

/**
 * Lifecycle of a row in {@code outbox_event}.
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHING,
    PUBLISHED,
    RETRY,
    DEAD_LETTER
}
