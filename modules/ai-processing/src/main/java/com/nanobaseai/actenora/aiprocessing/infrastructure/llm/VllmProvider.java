package com.nanobaseai.actenora.aiprocessing.infrastructure.llm;

import com.nanobaseai.actenora.aiprocessing.application.modelworker.InferenceResult;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.InferenceStreamChunk;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderCapabilities;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderHealth;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ResolvedInferenceInput;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.TokenEstimate;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.WorkerRequestEnvelope;
import com.nanobaseai.actenora.aiprocessing.application.port.LocalModelProvider;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * vLLM adapter. Uses vLLM's OpenAI-compatible HTTP API.
 */
public final class VllmProvider implements LocalModelProvider {

    private final OpenAiCompatibleLocalProvider delegate;

    public VllmProvider(LocalProviderConfig config) {
        Objects.requireNonNull(config, "config");
        this.delegate = new OpenAiCompatibleLocalProvider(config.withProviderKind("vllm"));
    }

    public VllmProvider(URI baseUrl) {
        this(LocalProviderConfig.builder("vllm", baseUrl).build());
    }

    OpenAiCompatibleLocalProvider delegate() {
        return delegate;
    }

    @Override
    public InferenceResult submitInference(WorkerRequestEnvelope envelope, ResolvedInferenceInput input) {
        return delegate.submitInference(envelope, input);
    }

    @Override
    public Stream<InferenceStreamChunk> streamInference(
            WorkerRequestEnvelope envelope,
            ResolvedInferenceInput input
    ) {
        return delegate.streamInference(envelope, input);
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
