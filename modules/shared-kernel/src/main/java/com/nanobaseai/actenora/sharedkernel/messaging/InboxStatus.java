package com.nanobaseai.actenora.sharedkernel.messaging;

/**
 * Lifecycle of a row in {@code inbox_event}.
 */
public enum InboxStatus {
    RECEIVED,
    PROCESSING,
    PROCESSED,
    FAILED,
    DEAD_LETTER
}
