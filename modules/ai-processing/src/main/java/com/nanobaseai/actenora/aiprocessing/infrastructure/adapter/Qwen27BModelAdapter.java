package com.nanobaseai.actenora.aiprocessing.infrastructure.adapter;

import com.nanobaseai.actenora.aiprocessing.application.pipeline.InferenceRequest;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.InferenceResponse;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelDescriptor;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelRuntimePort;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelUnavailableException;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ApproximateTokenEstimator;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TokenEstimator;

import java.util.Objects;
import java.util.function.Function;

/**
 * Qwen 27B production adapter — the only place that knows Qwen served-model identity.
 * Domain / application code depends on {@link ModelRuntimePort} only.
 * Cloud fallback is intentionally absent.
 */
public final class Qwen27BModelAdapter implements ModelRuntimePort {

    public static final String CATALOG_ID = "local.reasoner.default";
    public static final String SERVED_MODEL_ID = "qwen2.5-32b-instruct";
    public static final String MODEL_VERSION = "qwen2.5-32b-instruct@local-v1";
    public static final int CONTEXT_WINDOW = 32_768;
    public static final int MAX_OUTPUT = 2_048;

    private final Function<InferenceRequest, String> localGenerator;
    private final TokenEstimator tokenEstimator;
    private volatile boolean healthy;

    public Qwen27BModelAdapter(Function<InferenceRequest, String> localGenerator) {
        this(localGenerator, true);
    }

    public Qwen27BModelAdapter(Function<InferenceRequest, String> localGenerator, boolean healthy) {
        this.localGenerator = Objects.requireNonNull(localGenerator, "localGenerator");
        this.tokenEstimator = new ApproximateTokenEstimator();
        this.healthy = healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    @Override
    public ModelDescriptor descriptor() {
        return new ModelDescriptor(
                CATALOG_ID,
                SERVED_MODEL_ID,
                MODEL_VERSION,
                CONTEXT_WINDOW,
                MAX_OUTPUT
        );
    }

    @Override
    public boolean healthy() {
        return healthy;
    }

    @Override
    public InferenceResponse infer(InferenceRequest request) {
        if (!healthy) {
            throw new ModelUnavailableException("Local Qwen runtime unhealthy");
        }
        long started = System.nanoTime();
        String raw;
        try {
            raw = localGenerator.apply(request);
        } catch (RuntimeException ex) {
            throw new ModelUnavailableException("Local Qwen inference failed: " + ex.getMessage(), ex);
        }
        if (raw == null) {
            throw new ModelUnavailableException("Local Qwen returned null output");
        }
        long latencyMs = (System.nanoTime() - started) / 1_000_000L;
        long inputTokens = tokenEstimator.estimate(request.systemPrompt())
                + tokenEstimator.estimate(request.userPrompt());
        long outputTokens = tokenEstimator.estimate(raw);
        return new InferenceResponse(raw, inputTokens, outputTokens, latencyMs, MODEL_VERSION);
    }
}
