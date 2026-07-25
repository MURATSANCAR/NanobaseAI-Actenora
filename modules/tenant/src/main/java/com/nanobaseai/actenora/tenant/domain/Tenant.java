package com.nanobaseai.actenora.tenant.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Tenant {

    private final TenantId id;
    private String name;
    private TenantStatus status;
    private String timezone;
    private String defaultLanguage;
    private int retentionPolicyDays;
    private final String entraTenantId;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public Tenant(
            TenantId id,
            String name,
            TenantStatus status,
            String timezone,
            String defaultLanguage,
            int retentionPolicyDays,
            String entraTenantId,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = requireNonBlank(name, "name");
        this.status = Objects.requireNonNull(status, "status");
        this.timezone = requireNonBlank(timezone, "timezone");
        this.defaultLanguage = requireNonBlank(defaultLanguage, "defaultLanguage");
        if (retentionPolicyDays <= 0) {
            throw new IllegalArgumentException("retentionPolicyDays must be positive");
        }
        this.retentionPolicyDays = retentionPolicyDays;
        this.entraTenantId = requireNonBlank(entraTenantId, "entraTenantId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    public static Tenant provision(
            String name,
            String entraTenantId,
            String timezone,
            String defaultLanguage,
            int retentionPolicyDays,
            Instant now
    ) {
        return new Tenant(
                TenantId.of(UUID.randomUUID()),
                name,
                TenantStatus.ACTIVE,
                timezone == null || timezone.isBlank() ? "UTC" : timezone,
                defaultLanguage == null || defaultLanguage.isBlank() ? "en" : defaultLanguage,
                retentionPolicyDays <= 0 ? 365 : retentionPolicyDays,
                entraTenantId,
                now,
                now,
                0L
        );
    }

    public void suspend(long expectedVersion, Instant now) {
        assertVersion(expectedVersion);
        if (status == TenantStatus.SUSPENDED) {
            return;
        }
        this.status = TenantStatus.SUSPENDED;
        touch(now);
    }

    public void activate(long expectedVersion, Instant now) {
        assertVersion(expectedVersion);
        this.status = TenantStatus.ACTIVE;
        touch(now);
    }

    public void rename(String newName, long expectedVersion, Instant now) {
        assertVersion(expectedVersion);
        this.name = requireNonBlank(newName, "name");
        touch(now);
    }

    public void assertActive() {
        if (status != TenantStatus.ACTIVE) {
            throw new TenantNotActiveException(id, status);
        }
    }

    private void assertVersion(long expectedVersion) {
        if (this.version != expectedVersion) {
            throw new OptimisticLockException(id.value(), expectedVersion, this.version);
        }
    }

    private void touch(Instant now) {
        this.updatedAt = Objects.requireNonNull(now, "now");
        this.version++;
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public TenantId id() { return id; }
    public String name() { return name; }
    public TenantStatus status() { return status; }
    public String timezone() { return timezone; }
    public String defaultLanguage() { return defaultLanguage; }
    public int retentionPolicyDays() { return retentionPolicyDays; }
    public String entraTenantId() { return entraTenantId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
