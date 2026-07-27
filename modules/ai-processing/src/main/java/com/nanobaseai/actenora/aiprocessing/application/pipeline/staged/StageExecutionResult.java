package com.nanobaseai.actenora.aiprocessing.application.pipeline.staged;

import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingStage;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Result of executing a single pipeline stage.
 */
public record StageExecutionResult(
        AiJob job,
        boolean succeeded,
        boolean retryable,
        String artifactType,
        String artifactJson,
        String errorCode,
        String errorMessage,
        int inputTokens,
        int outputTokens,
        long latencyMs,
        Instant completedAt,
        /** When triage exits early, signal to skip extract/merge graph. */
        boolean earlyExitInformational
) {

    public StageExecutionResult {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(completedAt, "completedAt");
    }

    public static StageExecutionResult success(
            AiJob job,
            String artifactType,
            String artifactJson,
            int inputTokens,
            int outputTokens,
            long latencyMs,
            Instant completedAt
    ) {
        return new StageExecutionResult(
                job, true, false, artifactType, artifactJson, null, null,
                inputTokens, outputTokens, latencyMs, completedAt, false
        );
    }

    public static StageExecutionResult earlyExit(
            AiJob job,
            String artifactJson,
            int inputTokens,
            int outputTokens,
            long latencyMs,
            Instant completedAt
    ) {
        return new StageExecutionResult(
                job, true, false, "triage", artifactJson, null, null,
                inputTokens, outputTokens, latencyMs, completedAt, true
        );
    }

    public static StageExecutionResult failure(
            AiJob job,
            boolean retryable,
            String errorCode,
            String errorMessage,
            long latencyMs,
            Instant completedAt
    ) {
        return new StageExecutionResult(
                job, false, retryable, null, null, errorCode, errorMessage,
                0, 0, latencyMs, completedAt, false
        );
    }

    public ProcessingStage stage() {
        return job.stage();
    }
}
