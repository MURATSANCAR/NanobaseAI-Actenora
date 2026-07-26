package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.application.modelworker.InferenceResult;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.InferenceStreamChunk;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderCapabilities;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderHealth;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ResolvedInferenceInput;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.TokenEstimate;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.WorkerRequestEnvelope;
import com.nanobaseai.actenora.aiprocessing.application.port.LocalModelProvider;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Hot-swappable NanobaseAI local intelligence runtime.
 */
public final class SwappableLocalModelProvider implements LocalModelProvider {

    private final AtomicReference<LocalModelProvider> delegate;

    public SwappableLocalModelProvider(LocalModelProvider initial) {
        this.delegate = new AtomicReference<>(Objects.requireNonNull(initial, "initial"));
    }

    public void replace(LocalModelProvider next) {
        LocalModelProvider previous = delegate.getAndSet(Objects.requireNonNull(next, "next"));
        try {
            previous.beginDrain();
        } catch (RuntimeException ignored) {
            // best-effort drain
        }
    }

    public LocalModelProvider delegate() {
        return delegate.get();
    }

    @Override
    public InferenceResult submitInference(WorkerRequestEnvelope envelope, ResolvedInferenceInput input) {
        return delegate.get().submitInference(envelope, input);
    }

    @Override
    public Stream<InferenceStreamChunk> streamInference(WorkerRequestEnvelope envelope, ResolvedInferenceInput input) {
        return delegate.get().streamInference(envelope, input);
    }

    @Override
    public ProviderHealth health() {
        return delegate.get().health();
    }

    @Override
    public ProviderCapabilities capabilities() {
        return delegate.get().capabilities();
    }

    @Override
    public void cancel(UUID attemptId) {
        delegate.get().cancel(attemptId);
    }

    @Override
    public TokenEstimate estimateTokens(String text) {
        return delegate.get().estimateTokens(text);
    }

    @Override
    public void beginDrain() {
        delegate.get().beginDrain();
    }

    @Override
    public boolean isDraining() {
        return delegate.get().isDraining();
    }
}
