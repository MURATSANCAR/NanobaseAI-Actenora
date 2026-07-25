package com.nanobaseai.actenora.policy.domain;

/** Concurrent job ceilings (aligned with quota max_concurrent_ai_jobs). */
public record ConcurrencyPolicy(int maxConcurrentAiJobs, int maxConcurrentTranscriptJobs) {
    public ConcurrencyPolicy {
        if (maxConcurrentAiJobs < 0 || maxConcurrentTranscriptJobs < 0) {
            throw new IllegalArgumentException("concurrency limits must be non-negative");
        }
    }

    public static ConcurrencyPolicy systemDefaults() {
        return new ConcurrencyPolicy(4, 8);
    }
}
