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
        long latencyMs,
        long timeToFirstTokenMs
) {
    public InferenceResult(
            UUID jobId,
            UUID attemptId,
            String servedModelId,
            String content,
            TokenUsage tokenUsage,
            long latencyMs
    ) {
        this(jobId, attemptId, servedModelId, content, tokenUsage, latencyMs, -1L);
    }

    public InferenceResult {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(servedModelId, "servedModelId");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(tokenUsage, "tokenUsage");
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs must be >= 0");
        }
        if (timeToFirstTokenMs < -1L) {
            throw new IllegalArgumentException("timeToFirstTokenMs must be >= -1");
        }
    }

    /** {@code true} when streaming (or provider) reported a first-token timestamp. */
    public boolean hasTimeToFirstToken() {
        return timeToFirstTokenMs >= 0L;
    }
}
