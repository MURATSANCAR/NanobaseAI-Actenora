package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

import java.util.concurrent.atomic.AtomicLong;

/**
 * In-process counters for gate observability (hook for Micrometer later).
 */
public final class ChunkGateMetrics {

    public static final ChunkGateMetrics NOOP = new ChunkGateMetrics(true);

    private final boolean noop;
    private final AtomicLong total = new AtomicLong();
    private final AtomicLong skipped = new AtomicLong();
    private final AtomicLong extracted = new AtomicLong();
    private final AtomicLong continuation = new AtomicLong();
    private final AtomicLong shadowFalseNegative = new AtomicLong();
    private final AtomicLong tokensSaved = new AtomicLong();
    private final AtomicLong decisionUnsupported = new AtomicLong();

    private final AtomicLong classifierExtract = new AtomicLong();

    public ChunkGateMetrics() {
        this(false);
    }

    private ChunkGateMetrics(boolean noop) {
        this.noop = noop;
    }

    public void incrementTotal() {
        if (!noop) {
            total.incrementAndGet();
        }
    }

    public void incrementSkipped() {
        if (!noop) {
            skipped.incrementAndGet();
        }
    }

    public void incrementExtracted() {
        if (!noop) {
            extracted.incrementAndGet();
        }
    }

    public void incrementContinuation() {
        if (!noop) {
            continuation.incrementAndGet();
        }
    }

    public void incrementClassifierExtract() {
        if (!noop) {
            classifierExtract.incrementAndGet();
        }
    }

    public void incrementShadowFalseNegative() {
        if (!noop) {
            shadowFalseNegative.incrementAndGet();
        }
    }

    public void addTokensSaved(int tokens) {
        if (!noop && tokens > 0) {
            tokensSaved.addAndGet(tokens);
        }
    }

    public void incrementDecisionUnsupported() {
        if (!noop) {
            decisionUnsupported.incrementAndGet();
        }
    }

    public long total() {
        return total.get();
    }

    public long skipped() {
        return skipped.get();
    }

    public long extracted() {
        return extracted.get();
    }

    public long continuation() {
        return continuation.get();
    }

    public long classifierExtract() {
        return classifierExtract.get();
    }

    public long shadowFalseNegative() {
        return shadowFalseNegative.get();
    }

    public long tokensSaved() {
        return tokensSaved.get();
    }

    public long decisionUnsupported() {
        return decisionUnsupported.get();
    }
}
