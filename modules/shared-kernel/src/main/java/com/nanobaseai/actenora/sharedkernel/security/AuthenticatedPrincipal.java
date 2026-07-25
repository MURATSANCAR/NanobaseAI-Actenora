package com.nanobaseai.actenora.sharedkernel.security;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Authenticated caller resolved from the identity provider + tenant membership.
 * Permission and role codes are opaque strings owned by the Identity context.
 */
public record AuthenticatedPrincipal(
        TenantId tenantId,
        UUID userId,
        String entraObjectId,
        String email,
        String displayName,
        Set<String> roles,
        Set<String> permissions,
        boolean globalAdmin
) {

    public AuthenticatedPrincipal {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(entraObjectId, "entraObjectId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(displayName, "displayName");
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions"));
    }

    public boolean hasRole(String role) {
        return globalAdmin || roles.contains(role);
    }

    public boolean hasPermission(String permission) {
        return globalAdmin || permissions.contains(permission);
    }
}
