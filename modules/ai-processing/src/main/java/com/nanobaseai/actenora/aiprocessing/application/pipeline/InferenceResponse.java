package com.nanobaseai.actenora.aiprocessing.application.pipeline;

import java.util.Objects;

public record InferenceResponse(
        String rawText,
        long inputTokens,
        long outputTokens,
        long latencyMs,
        String modelVersion
) {
    public InferenceResponse {
        Objects.requireNonNull(rawText, "rawText");
        Objects.requireNonNull(modelVersion, "modelVersion");
    }
}
