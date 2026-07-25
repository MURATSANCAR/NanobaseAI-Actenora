package com.nanobaseai.actenora.approval.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One stage in an approval chain. V1 uses a single step; the model already supports N ordered steps.
 */
public final class ApprovalStep {

    private final UUID id;
    private final int stepOrder;
    private final String requiredApproverId;
    private final ApprovalStepStatus status;
    private final Instant decidedAt;

    private ApprovalStep(
            UUID id,
            int stepOrder,
            String requiredApproverId,
            ApprovalStepStatus status,
            Instant decidedAt
    ) {
        if (stepOrder < 1) {
            throw new IllegalArgumentException("stepOrder must be >= 1");
        }
        this.id = Objects.requireNonNull(id, "id");
        this.stepOrder = stepOrder;
        this.requiredApproverId = requireText(requiredApproverId, "requiredApproverId");
        this.status = Objects.requireNonNull(status, "status");
        this.decidedAt = decidedAt;
    }

    public static ApprovalStep pending(int stepOrder, String requiredApproverId) {
        return new ApprovalStep(UUID.randomUUID(), stepOrder, requiredApproverId, ApprovalStepStatus.PENDING, null);
    }

    public static ApprovalStep rehydrate(
            UUID id,
            int stepOrder,
            String requiredApproverId,
            ApprovalStepStatus status,
            Instant decidedAt
    ) {
        return new ApprovalStep(id, stepOrder, requiredApproverId, status, decidedAt);
    }

    public ApprovalStep markGranted(Instant now) {
        requirePending();
        return new ApprovalStep(id, stepOrder, requiredApproverId, ApprovalStepStatus.GRANTED, now);
    }

    public ApprovalStep markDenied(Instant now) {
        requirePending();
        return new ApprovalStep(id, stepOrder, requiredApproverId, ApprovalStepStatus.DENIED, now);
    }

    public boolean isAuthorized(String actorId) {
        return requiredApproverId.equals(Objects.requireNonNull(actorId, "actorId"));
    }

    public boolean isPending() {
        return status == ApprovalStepStatus.PENDING;
    }

    private void requirePending() {
        if (status != ApprovalStepStatus.PENDING) {
            throw new IllegalStateException("step is already " + status);
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public UUID id() {
        return id;
    }

    public int stepOrder() {
        return stepOrder;
    }

    public String requiredApproverId() {
        return requiredApproverId;
    }

    public ApprovalStepStatus status() {
        return status;
    }

    public Instant decidedAt() {
        return decidedAt;
    }
}
