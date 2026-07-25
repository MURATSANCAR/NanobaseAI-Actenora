package com.nanobaseai.actenora.aiprocessing.application.modelworker;

import java.util.Objects;
import java.util.UUID;

/**
 * Successful non-streaming inference result. {@code content} must not be logged by adapters.
 */
public record InferenceResult(
        UUID jobId,
        UUID attemptId,
        String servedModelId,
        String content,
        TokenUsage tokenUsage,
        long latencyMs
) {
    public InferenceResult {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(servedModelId, "servedModelId");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(tokenUsage, "tokenUsage");
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs must be >= 0");
        }
    }
}
