package com.nanobaseai.actenora.aiprocessing.application.modelworker;

import java.util.Objects;

/**
 * One streaming chunk. Delta text must not be logged.
 */
public record InferenceStreamChunk(
        String delta,
        boolean done,
        TokenUsage tokenUsage
) {
    public InferenceStreamChunk {
        Objects.requireNonNull(delta, "delta");
        if (done && tokenUsage == null) {
            tokenUsage = TokenUsage.unknown();
        }
    }

    public static InferenceStreamChunk delta(String text) {
        return new InferenceStreamChunk(text, false, null);
    }

    public static InferenceStreamChunk done(TokenUsage usage) {
        return new InferenceStreamChunk("", true, usage == null ? TokenUsage.unknown() : usage);
    }
}
