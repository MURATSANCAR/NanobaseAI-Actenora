package com.nanobaseai.actenora.approval.domain;

import com.nanobaseai.actenora.approval.api.ApprovalDecisionType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record of a human decision on an approval step. Comment is first-class.
 */
public final class ApprovalDecision {

    private final UUID id;
    private final UUID approvalRequestId;
    private final UUID stepId;
    private final ApprovalDecisionType decisionType;
    private final String decidedBy;
    private final String comment;
    private final Instant decidedAt;

    private ApprovalDecision(
            UUID id,
            UUID approvalRequestId,
            UUID stepId,
            ApprovalDecisionType decisionType,
            String decidedBy,
            String comment,
            Instant decidedAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.approvalRequestId = Objects.requireNonNull(approvalRequestId, "approvalRequestId");
        this.stepId = Objects.requireNonNull(stepId, "stepId");
        this.decisionType = Objects.requireNonNull(decisionType, "decisionType");
        this.decidedBy = requireText(decidedBy, "decidedBy");
        this.comment = comment == null ? "" : comment.trim();
        this.decidedAt = Objects.requireNonNull(decidedAt, "decidedAt");
    }

    public static ApprovalDecision record(
            UUID approvalRequestId,
            UUID stepId,
            ApprovalDecisionType decisionType,
            String decidedBy,
            String comment,
            Instant now
    ) {
        return new ApprovalDecision(
                UUID.randomUUID(),
                approvalRequestId,
                stepId,
                decisionType,
                decidedBy,
                comment,
                now
        );
    }

    public static ApprovalDecision rehydrate(
            UUID id,
            UUID approvalRequestId,
            UUID stepId,
            ApprovalDecisionType decisionType,
            String decidedBy,
            String comment,
            Instant decidedAt
    ) {
        return new ApprovalDecision(id, approvalRequestId, stepId, decisionType, decidedBy, comment, decidedAt);
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

    public UUID approvalRequestId() {
        return approvalRequestId;
    }

    public UUID stepId() {
        return stepId;
    }

    public ApprovalDecisionType decisionType() {
        return decisionType;
    }

    public String decidedBy() {
        return decidedBy;
    }

    public String comment() {
        return comment;
    }

    public Instant decidedAt() {
        return decidedAt;
    }
}
