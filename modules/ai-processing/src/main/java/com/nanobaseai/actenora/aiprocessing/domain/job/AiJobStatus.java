package com.nanobaseai.actenora.aiprocessing.domain.job;

/**
 * Inference job lifecycle (STATE-MACHINES.md / FAZ 12).
 */
public enum AiJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    DEAD;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == CANCELLED || this == DEAD;
    }

    public boolean isActive() {
        return this == QUEUED || this == RUNNING;
    }
}
