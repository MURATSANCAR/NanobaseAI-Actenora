package com.nanobaseai.actenora.identity.domain;

import com.nanobaseai.actenora.identity.api.Permission;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class User {

    private final UUID id;
    private final TenantId tenantId;
    private final String entraObjectId;
    private String email;
    private String displayName;
    private UserStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;
    private final Set<SystemRole> roles;

    public User(
            UUID id,
            TenantId tenantId,
            String entraObjectId,
            String email,
            String displayName,
            UserStatus status,
            Instant createdAt,
            Instant updatedAt,
            long version,
            Set<SystemRole> roles
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.entraObjectId = requireNonBlank(entraObjectId, "entraObjectId");
        this.email = requireNonBlank(email, "email");
        this.displayName = requireNonBlank(displayName, "displayName");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
        this.roles = EnumSet.copyOf(Objects.requireNonNull(roles, "roles"));
    }

    public static User provision(
            TenantId tenantId,
            String entraObjectId,
            String email,
            String displayName,
            SystemRole initialRole,
            Instant now
    ) {
        return new User(
                UUID.randomUUID(),
                tenantId,
                entraObjectId,
                email,
                displayName,
                UserStatus.ACTIVE,
                now,
                now,
                0L,
                EnumSet.of(initialRole)
        );
    }

    public void assertActive() {
        if (status != UserStatus.ACTIVE) {
            throw new UserNotActiveException(id, status);
        }
    }

    public void grantRole(SystemRole role, long expectedVersion, Instant now) {
        assertVersion(expectedVersion);
        roles.add(Objects.requireNonNull(role, "role"));
        touch(now);
    }

    public void revokeRole(SystemRole role, long expectedVersion, Instant now) {
        assertVersion(expectedVersion);
        if (role == SystemRole.SUPER_ADMIN && roles.contains(SystemRole.SUPER_ADMIN) && roles.size() == 1) {
            throw new IllegalStateException("Cannot revoke the last SUPER_ADMIN role");
        }
        roles.remove(Objects.requireNonNull(role, "role"));
        touch(now);
    }

    public void disable(long expectedVersion, Instant now) {
        assertVersion(expectedVersion);
        this.status = UserStatus.DISABLED;
        touch(now);
    }

    public Set<Permission> effectivePermissions() {
        return RolePermissionCatalog.permissionsFor(roles);
    }

    private void assertVersion(long expectedVersion) {
        if (this.version != expectedVersion) {
            throw new OptimisticLockException(id, expectedVersion, this.version);
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

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public String entraObjectId() { return entraObjectId; }
    public String email() { return email; }
    public String displayName() { return displayName; }
    public UserStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
    public Set<SystemRole> roles() { return EnumSet.copyOf(roles); }
}
