package com.nanobaseai.actenora.approval.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Structured feedback when an approver requests changes instead of granting/denying.
 */
public final class ChangeRequest {

    private final UUID id;
    private final UUID approvalRequestId;
    private final UUID subjectId;
    private final String requestedBy;
    private final String reason;
    private final ChangeRequestStatus status;
    private final Instant createdAt;
    private final Instant resolvedAt;

    private ChangeRequest(
            UUID id,
            UUID approvalRequestId,
            UUID subjectId,
            String requestedBy,
            String reason,
            ChangeRequestStatus status,
            Instant createdAt,
            Instant resolvedAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.approvalRequestId = Objects.requireNonNull(approvalRequestId, "approvalRequestId");
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        this.requestedBy = requireText(requestedBy, "requestedBy");
        this.reason = requireText(reason, "reason");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.resolvedAt = resolvedAt;
    }

    public static ChangeRequest open(
            UUID approvalRequestId,
            UUID subjectId,
            String requestedBy,
            String reason,
            Instant now
    ) {
        return new ChangeRequest(
                UUID.randomUUID(),
                approvalRequestId,
                subjectId,
                requestedBy,
                reason,
                ChangeRequestStatus.OPEN,
                now,
                null
        );
    }

    public static ChangeRequest rehydrate(
            UUID id,
            UUID approvalRequestId,
            UUID subjectId,
            String requestedBy,
            String reason,
            ChangeRequestStatus status,
            Instant createdAt,
            Instant resolvedAt
    ) {
        return new ChangeRequest(
                id, approvalRequestId, subjectId, requestedBy, reason, status, createdAt, resolvedAt
        );
    }

    public ChangeRequest markAddressed(Instant now) {
        requireOpen();
        return new ChangeRequest(
                id, approvalRequestId, subjectId, requestedBy, reason,
                ChangeRequestStatus.ADDRESSED, createdAt, now
        );
    }

    public ChangeRequest dismiss(Instant now) {
        requireOpen();
        return new ChangeRequest(
                id, approvalRequestId, subjectId, requestedBy, reason,
                ChangeRequestStatus.DISMISSED, createdAt, now
        );
    }

    private void requireOpen() {
        if (status != ChangeRequestStatus.OPEN) {
            throw new IllegalStateException("change request is already " + status);
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

    public UUID approvalRequestId() {
        return approvalRequestId;
    }

    public UUID subjectId() {
        return subjectId;
    }

    public String requestedBy() {
        return requestedBy;
    }

    public String reason() {
        return reason;
    }

    public ChangeRequestStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant resolvedAt() {
        return resolvedAt;
    }
}
