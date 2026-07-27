package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.application.modelworker.InferenceResult;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.InferenceStreamChunk;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.LocalModelProviderException;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderCapabilities;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderFailureCategory;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderHealth;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ResolvedInferenceInput;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.TokenEstimate;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.WorkerRequestEnvelope;
import com.nanobaseai.actenora.aiprocessing.application.port.LocalModelProvider;
import com.nanobaseai.actenora.sharedkernel.coordination.FixedWindowRateLimiter;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Short-lived LLM request budget. Durable tenant quota stays in PostgreSQL policy module.
 */
public final class RateLimitedLocalModelProvider implements LocalModelProvider {

    private final LocalModelProvider delegate;
    private final FixedWindowRateLimiter rateLimiter;
    private final int limit;
    private final Duration window;

    public RateLimitedLocalModelProvider(
            LocalModelProvider delegate,
            FixedWindowRateLimiter rateLimiter,
            int limit,
            Duration window
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        this.limit = limit;
        this.window = Objects.requireNonNull(window, "window");
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
    }

    @Override
    public InferenceResult submitInference(WorkerRequestEnvelope envelope, ResolvedInferenceInput input) {
        acquireOrReject(envelope);
        return delegate.submitInference(envelope, input);
    }

    @Override
    public Stream<InferenceStreamChunk> streamInference(
            WorkerRequestEnvelope envelope,
            ResolvedInferenceInput input
    ) {
        acquireOrReject(envelope);
        return delegate.streamInference(envelope, input);
    }

    private void acquireOrReject(WorkerRequestEnvelope envelope) {
        String key = "llm:model:" + envelope.servedModelId();
        if (!rateLimiter.tryAcquire(key, limit, window)) {
            throw LocalModelProviderException.of(
                    ProviderFailureCategory.CONCURRENCY_LIMIT,
                    "llm rate limit exceeded for servedModelId=" + envelope.servedModelId(),
                    true
            );
        }
    }

    @Override
    public ProviderHealth health() {
        return delegate.health();
    }

    @Override
    public ProviderCapabilities capabilities() {
        return delegate.capabilities();
    }

    @Override
    public void cancel(UUID attemptId) {
        delegate.cancel(attemptId);
    }

    @Override
    public TokenEstimate estimateTokens(String text) {
        return delegate.estimateTokens(text);
    }

    @Override
    public void beginDrain() {
        delegate.beginDrain();
    }

    @Override
    public boolean isDraining() {
        return delegate.isDraining();
    }
}
