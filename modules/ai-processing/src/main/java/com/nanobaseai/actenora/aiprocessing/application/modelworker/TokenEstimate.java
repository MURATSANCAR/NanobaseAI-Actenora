package com.nanobaseai.actenora.aiprocessing.application.modelworker;

/**
 * Pre-flight token estimate. Heuristic adapters may report an approximate count.
 */
public record TokenEstimate(int tokens, boolean approximate) {
    public TokenEstimate {
        if (tokens < 0) {
            throw new IllegalArgumentException("tokens must be >= 0");
        }
    }

    public static TokenEstimate approximate(int tokens) {
        return new TokenEstimate(tokens, true);
    }

    public static TokenEstimate exact(int tokens) {
        return new TokenEstimate(tokens, false);
    }
}
