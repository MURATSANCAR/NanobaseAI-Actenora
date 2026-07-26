package com.nanobaseai.actenora.operations.domain.retention;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * FAZ 27 legal-hold preparation — active holds block retention deletion.
 * Tenant policy {@code legalHoldAllowed} gates whether holds may be placed.
 */
public final class LegalHold {

    private final UUID id;
    private final TenantId tenantId;
    private final RetentionResourceType resourceType;
    private final String resourceId;
    private final String reason;
    private final UUID placedByUserId;
    private final Instant placedAt;
    private final Instant releasedAt;

    private LegalHold(
            UUID id,
            TenantId tenantId,
            RetentionResourceType resourceType,
            String resourceId,
            String reason,
            UUID placedByUserId,
            Instant placedAt,
            Instant releasedAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType");
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.placedByUserId = placedByUserId;
        this.placedAt = Objects.requireNonNull(placedAt, "placedAt");
        this.releasedAt = releasedAt;
    }

    public static LegalHold place(
            TenantId tenantId,
            RetentionResourceType resourceType,
            String resourceId,
            String reason,
            UUID placedByUserId,
            Instant now
    ) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        return new LegalHold(
                UUID.randomUUID(),
                tenantId,
                resourceType,
                resourceId,
                reason.trim(),
                placedByUserId,
                now,
                null);
    }

    public static LegalHold rehydrate(
            UUID id,
            TenantId tenantId,
            RetentionResourceType resourceType,
            String resourceId,
            String reason,
            UUID placedByUserId,
            Instant placedAt,
            Instant releasedAt
    ) {
        return new LegalHold(
                id, tenantId, resourceType, resourceId, reason, placedByUserId, placedAt, releasedAt);
    }

    public LegalHold release(Instant now) {
        if (!isActive()) {
            return this;
        }
        return new LegalHold(
                id, tenantId, resourceType, resourceId, reason, placedByUserId, placedAt,
                Objects.requireNonNull(now, "now"));
    }

    public boolean isActive() {
        return releasedAt == null;
    }

    public boolean covers(RetentionCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return isActive()
                && tenantId.equals(candidate.tenantId())
                && resourceType == candidate.resourceType()
                && resourceId.equals(candidate.resourceId());
    }

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public RetentionResourceType resourceType() { return resourceType; }
    public String resourceId() { return resourceId; }
    public String reason() { return reason; }
    public Optional<UUID> placedByUserId() { return Optional.ofNullable(placedByUserId); }
    public Instant placedAt() { return placedAt; }
    public Optional<Instant> releasedAt() { return Optional.ofNullable(releasedAt); }
}
