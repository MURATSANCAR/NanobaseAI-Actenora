package com.nanobaseai.actenora.modelmanagement.application;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Authenticated actor calling the control plane (resolved by Identity in FAZ 4).
 */
public record ActorPrincipal(
        UUID userId,
        String role,
        Set<ModelControlPermission> permissions
) {
    public ActorPrincipal {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
        permissions = permissions == null
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(permissions));
    }

    public boolean has(ModelControlPermission permission) {
        return permissions.contains(permission);
    }

    public static ActorPrincipal of(UUID userId, String role, Set<ModelControlPermission> permissions) {
        return new ActorPrincipal(userId, role, permissions);
    }

    public static ActorPrincipal operationsAdmin(UUID userId) {
        return new ActorPrincipal(userId, "OPERATIONS", EnumSet.allOf(ModelControlPermission.class));
    }
}
