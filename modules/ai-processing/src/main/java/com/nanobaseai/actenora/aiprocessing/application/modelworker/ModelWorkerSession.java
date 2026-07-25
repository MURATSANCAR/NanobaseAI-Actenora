package com.nanobaseai.actenora.aiprocessing.application.modelworker;

import com.nanobaseai.actenora.aiprocessing.application.port.LocalModelProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Model worker protocol facade: heartbeat, graceful drain, backpressure accounting,
 * and safe delegation to a {@link LocalModelProvider}.
 */
public final class ModelWorkerSession {

    private final String workerId;
    private final LocalModelProvider provider;
    private final int maxConcurrency;
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final AtomicInteger inFlight = new AtomicInteger(0);
    private volatile HealthStatus lastHealthStatus = HealthStatus.DOWN;

    public ModelWorkerSession(String workerId, LocalModelProvider provider, int maxConcurrency) {
        this.workerId = Objects.requireNonNull(workerId, "workerId");
        this.provider = Objects.requireNonNull(provider, "provider");
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("maxConcurrency must be >= 1");
        }
        this.maxConcurrency = maxConcurrency;
    }

    public InferenceResult submit(WorkerRequestEnvelope envelope, ResolvedInferenceInput input) {
        rejectIfDraining();
        inFlight.incrementAndGet();
        try {
            return provider.submitInference(envelope, input);
        } finally {
            inFlight.decrementAndGet();
        }
    }

    public Stream<InferenceStreamChunk> stream(WorkerRequestEnvelope envelope, ResolvedInferenceInput input) {
        rejectIfDraining();
        inFlight.incrementAndGet();
        try {
            // Materialize under in-flight accounting; adapters that stream lazily should keep permits.
            return provider.streamInference(envelope, input).toList().stream();
        } finally {
            inFlight.decrementAndGet();
        }
    }

    public ProviderHealth probeHealth() {
        ProviderHealth health = provider.health();
        lastHealthStatus = health.status();
        return health;
    }

    public ProviderCapabilities capabilities() {
        return provider.capabilities();
    }

    public void cancel(UUID attemptId) {
        provider.cancel(attemptId);
    }

    public TokenEstimate estimateTokens(String text) {
        return provider.estimateTokens(text);
    }

    public void beginDrain() {
        draining.set(true);
        provider.beginDrain();
    }

    public boolean isDraining() {
        return draining.get() || provider.isDraining();
    }

    /**
     * Blocks until in-flight work reaches zero or timeout elapses.
     *
     * @return true if quiescent
     */
    public boolean awaitQuiescence(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        long deadline = System.nanoTime() + timeout.toNanos();
        while (inFlight.get() > 0) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            Thread.sleep(10);
        }
        return true;
    }

    public WorkerHeartbeat heartbeat() {
        WorkerHeartbeat snapshot = new WorkerHeartbeat(
                workerId,
                Instant.now(),
                draining.get(),
                inFlight.get(),
                maxConcurrency,
                lastHealthStatus
        );
        SafeInferenceLog.heartbeat(snapshot);
        return snapshot;
    }

    public int inFlight() {
        return inFlight.get();
    }

    private void rejectIfDraining() {
        if (draining.get()) {
            throw LocalModelProviderException.of(
                    ProviderFailureCategory.DRAINING,
                    "worker is draining",
                    true
            );
        }
    }
}
