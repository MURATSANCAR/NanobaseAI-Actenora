package com.nanobaseai.actenora.aiprocessing.application.pipeline;

/**
 * Local model runtime port. Cloud fallback is intentionally unsupported.
 */
public interface ModelRuntimePort {

    ModelDescriptor descriptor();

    InferenceResponse infer(InferenceRequest request);

    boolean healthy();
}
