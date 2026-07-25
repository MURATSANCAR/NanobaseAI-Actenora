package com.nanobaseai.actenora.operations.domain;

import java.util.Objects;

/**
 * Named queue depth for the queue dashboard.
 */
public record QueueDepth(
        String queueName,
        long ready,
        long unacked,
        long consumers
) {
    public QueueDepth {
        Objects.requireNonNull(queueName, "queueName");
        if (ready < 0 || unacked < 0 || consumers < 0) {
            throw new IllegalArgumentException("queue counts must be non-negative");
        }
    }

    public long depth() {
        return ready + unacked;
    }
}
