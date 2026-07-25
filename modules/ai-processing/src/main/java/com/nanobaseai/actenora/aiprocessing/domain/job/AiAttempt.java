package com.nanobaseai.actenora.aiprocessing.domain.job;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Single attempt to execute an AI job on a model deployment.
 */
public final class AiAttempt {

    private final UUID id;
    private final UUID aiJobId;
    private final int attemptNumber;
    private final UUID modelDefinitionId;
    private final UUID modelDeploymentId;
    private AiAttemptStatus status;
    private Long latencyMs;
    private Integer inputTokens;
    private Integer outputTokens;
    private boolean retryable;
    private String failureCategory;
    private String failureDetailSafe;
    private final Instant startedAt;
    private Instant completedAt;

    public AiAttempt(
            UUID id,
            UUID aiJobId,
            int attemptNumber,
            UUID modelDefinitionId,
            UUID modelDeploymentId,
            AiAttemptStatus status,
            Long latencyMs,
            Integer inputTokens,
            Integer outputTokens,
            boolean retryable,
            String failureCategory,
            String failureDetailSafe,
            Instant startedAt,
            Instant completedAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.aiJobId = Objects.requireNonNull(aiJobId, "aiJobId");
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be >= 1");
        }
        this.attemptNumber = attemptNumber;
        this.modelDefinitionId = Objects.requireNonNull(modelDefinitionId, "modelDefinitionId");
        this.modelDeploymentId = Objects.requireNonNull(modelDeploymentId, "modelDeploymentId");
        this.status = Objects.requireNonNull(status, "status");
        this.latencyMs = latencyMs;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.retryable = retryable;
        this.failureCategory = failureCategory;
        this.failureDetailSafe = failureDetailSafe;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.completedAt = completedAt;
    }

    public static AiAttempt start(
            UUID aiJobId,
            int attemptNumber,
            UUID modelDefinitionId,
            UUID modelDeploymentId,
            Instant now
    ) {
        return new AiAttempt(
                UUID.randomUUID(),
                aiJobId,
                attemptNumber,
                modelDefinitionId,
                modelDeploymentId,
                AiAttemptStatus.STARTED,
                null,
                null,
                null,
                false,
                null,
                null,
                now,
                null
        );
    }

    public void completeSuccess(long latencyMs, int inputTokens, int outputTokens, Instant now) {
        ensureStarted();
        this.status = AiAttemptStatus.SUCCEEDED;
        this.latencyMs = latencyMs;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.retryable = false;
        this.completedAt = Objects.requireNonNull(now, "now");
    }

    public void completeFailure(
            long latencyMs,
            boolean retryable,
            String failureCategory,
            String failureDetailSafe,
            Instant now
    ) {
        ensureStarted();
        this.status = AiAttemptStatus.FAILED;
        this.latencyMs = latencyMs;
        this.retryable = retryable;
        this.failureCategory = failureCategory;
        this.failureDetailSafe = failureDetailSafe;
        this.completedAt = Objects.requireNonNull(now, "now");
    }

    public void cancel(Instant now) {
        if (status != AiAttemptStatus.STARTED) {
            return;
        }
        this.status = AiAttemptStatus.CANCELLED;
        this.completedAt = Objects.requireNonNull(now, "now");
        this.retryable = false;
    }

    private void ensureStarted() {
        if (status != AiAttemptStatus.STARTED) {
            throw AiJobException.invalidTransition("Attempt is not STARTED: " + status);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID aiJobId() {
        return aiJobId;
    }

    public int attemptNumber() {
        return attemptNumber;
    }

    public UUID modelDefinitionId() {
        return modelDefinitionId;
    }

    public UUID modelDeploymentId() {
        return modelDeploymentId;
    }

    public AiAttemptStatus status() {
        return status;
    }

    public Optional<Long> latencyMs() {
        return Optional.ofNullable(latencyMs);
    }

    public Optional<Integer> inputTokens() {
        return Optional.ofNullable(inputTokens);
    }

    public Optional<Integer> outputTokens() {
        return Optional.ofNullable(outputTokens);
    }

    public boolean retryable() {
        return retryable;
    }

    public Optional<String> failureCategory() {
        return Optional.ofNullable(failureCategory);
    }

    public Optional<String> failureDetailSafe() {
        return Optional.ofNullable(failureDetailSafe);
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Optional<Instant> completedAt() {
        return Optional.ofNullable(completedAt);
    }
}
