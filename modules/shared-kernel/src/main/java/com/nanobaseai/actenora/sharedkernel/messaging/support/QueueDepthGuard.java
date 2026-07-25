package com.nanobaseai.actenora.sharedkernel.messaging.support;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Soft backpressure for outbox / work queues (FAZ 28). When depth exceeds
 * {@code maxDepth}, new admissions are rejected so queues cannot grow unbounded.
 */
public final class QueueDepthGuard {

    private final int maxDepth;
    private final AtomicInteger depth = new AtomicInteger();
    private final AtomicInteger rejected = new AtomicInteger();

    public QueueDepthGuard(int maxDepth) {
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be >= 1");
        }
        this.maxDepth = maxDepth;
    }

    public boolean tryAdmit() {
        while (true) {
            int current = depth.get();
            if (current >= maxDepth) {
                rejected.incrementAndGet();
                return false;
            }
            if (depth.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    public void release() {
        int after = depth.decrementAndGet();
        if (after < 0) {
            depth.set(0);
        }
    }

    public void observe(int absoluteDepth) {
        if (absoluteDepth < 0) {
            throw new IllegalArgumentException("absoluteDepth must be >= 0");
        }
        depth.set(absoluteDepth);
    }

    /** True when depth has exceeded the configured max (uncontrolled growth). */
    public boolean isOverLimit() {
        return depth.get() > maxDepth;
    }

    public boolean isAtCapacity() {
        return depth.get() >= maxDepth;
    }

    public int depth() {
        return depth.get();
    }

    public int maxDepth() {
        return maxDepth;
    }

    public int rejectedCount() {
        return rejected.get();
    }

    public void requireWithinLimit(String queueName) {
        Objects.requireNonNull(queueName, "queueName");
        if (isOverLimit()) {
            throw new IllegalStateException(
                    "Queue backlog uncontrolled: " + queueName + " depth=" + depth.get() + " max=" + maxDepth);
        }
    }
}
