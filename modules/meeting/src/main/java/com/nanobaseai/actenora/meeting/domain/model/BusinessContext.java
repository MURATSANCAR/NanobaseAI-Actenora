package com.nanobaseai.actenora.meeting.domain.model;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class BusinessContext {

    private final UUID id;
    private final TenantId tenantId;
    private String type;
    private String referenceCode;
    private String name;
    private String description;
    private BusinessContextStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    private BusinessContext(
            UUID id,
            TenantId tenantId,
            String type,
            String referenceCode,
            String name,
            String description,
            BusinessContextStatus status,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.type = requireText(type, "type");
        this.referenceCode = requireText(referenceCode, "referenceCode");
        this.name = requireText(name, "name");
        this.description = description;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    public static BusinessContext create(
            TenantId tenantId,
            String type,
            String referenceCode,
            String name,
            String description,
            Instant now
    ) {
        return new BusinessContext(
                UUID.randomUUID(),
                tenantId,
                type,
                referenceCode,
                name,
                description,
                BusinessContextStatus.ACTIVE,
                now,
                now,
                0L
        );
    }

    public static BusinessContext rehydrate(
            UUID id,
            TenantId tenantId,
            String type,
            String referenceCode,
            String name,
            String description,
            BusinessContextStatus status,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        return new BusinessContext(
                id, tenantId, type, referenceCode, name, description, status, createdAt, updatedAt, version
        );
    }

    public void update(String type, String referenceCode, String name, String description,
                       BusinessContextStatus status, long expectedVersion, Instant now) {
        assertVersion(expectedVersion);
        if (type != null) {
            this.type = requireText(type, "type");
        }
        if (referenceCode != null) {
            this.referenceCode = requireText(referenceCode, "referenceCode");
        }
        if (name != null) {
            this.name = requireText(name, "name");
        }
        if (description != null) {
            this.description = description;
        }
        if (status != null) {
            this.status = status;
        }
        this.updatedAt = now;
        this.version = expectedVersion + 1;
    }

    public void assertVersion(long expectedVersion) {
        if (this.version != expectedVersion) {
            throw new com.nanobaseai.actenora.meeting.domain.exception.OptimisticLockConflictException(id, expectedVersion);
        }
    }

    public void assertTenant(TenantId tenantId) {
        if (!this.tenantId.equals(tenantId)) {
            throw new com.nanobaseai.actenora.meeting.domain.exception.TenantIsolationViolationException();
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public String type() { return type; }
    public String referenceCode() { return referenceCode; }
    public String name() { return name; }
    public String description() { return description; }
    public BusinessContextStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
