package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-process counters + optional listener (Micrometer bridge).
 */
public final class ChunkGateMetrics {

    public static final ChunkGateMetrics NOOP = new ChunkGateMetrics(true, ChunkGateMetricListener.NOOP);

    private final boolean noop;
    private final ChunkGateMetricListener listener;
    private final AtomicLong total = new AtomicLong();
    private final AtomicLong skipped = new AtomicLong();
    private final AtomicLong extracted = new AtomicLong();
    private final AtomicLong continuation = new AtomicLong();
    private final AtomicLong shadowFalseNegative = new AtomicLong();
    private final AtomicLong tokensSaved = new AtomicLong();
    private final AtomicLong decisionUnsupported = new AtomicLong();
    private final AtomicLong classifierExtract = new AtomicLong();
    private volatile String lastPolicyVersion = "unknown";

    public ChunkGateMetrics() {
        this(false, ChunkGateMetricListener.NOOP);
    }

    public ChunkGateMetrics(ChunkGateMetricListener listener) {
        this(false, listener == null ? ChunkGateMetricListener.NOOP : listener);
    }

    private ChunkGateMetrics(boolean noop, ChunkGateMetricListener listener) {
        this.noop = noop;
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    public void setPolicyVersion(String policyVersion) {
        if (policyVersion != null && !policyVersion.isBlank()) {
            this.lastPolicyVersion = policyVersion;
        }
    }

    public void incrementTotal() {
        if (!noop) {
            total.incrementAndGet();
            listener.onTotal(lastPolicyVersion);
        }
    }

    public void incrementSkipped() {
        incrementSkipped(0);
    }

    public void incrementSkipped(int tokens) {
        if (!noop) {
            skipped.incrementAndGet();
            if (tokens > 0) {
                tokensSaved.addAndGet(tokens);
            }
            listener.onSkipped(lastPolicyVersion, tokens);
        }
    }

    public void incrementExtracted() {
        if (!noop) {
            extracted.incrementAndGet();
            listener.onExtracted(lastPolicyVersion);
        }
    }

    public void incrementContinuation() {
        if (!noop) {
            continuation.incrementAndGet();
            listener.onContinuation(lastPolicyVersion);
        }
    }

    public void incrementClassifierExtract() {
        if (!noop) {
            classifierExtract.incrementAndGet();
            listener.onClassifierExtract(lastPolicyVersion);
        }
    }

    public void incrementShadowFalseNegative() {
        if (!noop) {
            shadowFalseNegative.incrementAndGet();
            listener.onShadowFalseNegative(lastPolicyVersion);
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
            listener.onDecisionUnsupported(lastPolicyVersion);
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
