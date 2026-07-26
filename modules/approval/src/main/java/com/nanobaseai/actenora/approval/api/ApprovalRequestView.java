package com.nanobaseai.actenora.approval.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tenant-scoped approval snapshot for cross-module / HTTP consumers.
 */
public record ApprovalRequestView(
        ApprovalId id,
        UUID tenantId,
        ApprovalSubjectType subjectType,
        UUID subjectId,
        ApprovalRequestStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt
) {
    public ApprovalRequestView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(subjectType, "subjectType");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
