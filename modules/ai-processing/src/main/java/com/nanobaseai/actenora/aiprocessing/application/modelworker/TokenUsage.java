package com.nanobaseai.actenora.aiprocessing.application.modelworker;

/**
 * Token usage metrics for an inference attempt. Safe to log and persist.
 */
public record TokenUsage(int inputTokens, int outputTokens) {
    public TokenUsage {
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("token counts must be >= 0");
        }
    }

    public int totalTokens() {
        return inputTokens + outputTokens;
    }

    public static TokenUsage of(int inputTokens, int outputTokens) {
        return new TokenUsage(inputTokens, outputTokens);
    }

    public static TokenUsage unknown() {
        return new TokenUsage(0, 0);
    }
}
