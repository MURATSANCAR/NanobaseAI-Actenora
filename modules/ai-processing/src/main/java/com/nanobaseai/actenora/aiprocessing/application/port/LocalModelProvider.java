package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.application.modelworker.InferenceResult;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.InferenceStreamChunk;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderCapabilities;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderHealth;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ResolvedInferenceInput;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.TokenEstimate;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.WorkerRequestEnvelope;

import java.util.UUID;
import java.util.stream.Stream;

/**
 * Port that isolates business code from local LLM serving technology
 * (llama.cpp, vLLM, OpenAI-compatible proxies, mocks).
 *
 * <p>Implementations must never log raw prompts or raw model responses.
 */
public interface LocalModelProvider {

    InferenceResult submitInference(WorkerRequestEnvelope envelope, ResolvedInferenceInput input);

    /**
     * Optional streaming path. Providers that do not support streaming throw
     * {@code STREAMING_NOT_SUPPORTED}.
     */
    Stream<InferenceStreamChunk> streamInference(WorkerRequestEnvelope envelope, ResolvedInferenceInput input);

    ProviderHealth health();

    ProviderCapabilities capabilities();

    void cancel(UUID attemptId);

    TokenEstimate estimateTokens(String text);

    /** Graceful drain: reject new work while allowing in-flight calls to finish. */
    default void beginDrain() {
    }

    default boolean isDraining() {
        return false;
    }
}
