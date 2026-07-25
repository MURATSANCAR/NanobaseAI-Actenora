package com.nanobaseai.actenora.aiprocessing.domain.job;

/**
 * Scheduling / SLA priority for AI jobs.
 */
public enum JobPriority {
    CRITICAL(400),
    HIGH(300),
    NORMAL(200),
    BULK(100);

    private final int baseScore;

    JobPriority(int baseScore) {
        this.baseScore = baseScore;
    }

    public int baseScore() {
        return baseScore;
    }

    public boolean isCritical() {
        return this == CRITICAL;
    }
}
