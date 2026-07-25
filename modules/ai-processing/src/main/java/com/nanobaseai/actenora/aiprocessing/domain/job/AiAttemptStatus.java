package com.nanobaseai.actenora.aiprocessing.domain.job;

/**
 * Status of a single execution attempt against a model deployment.
 */
public enum AiAttemptStatus {
    STARTED,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
