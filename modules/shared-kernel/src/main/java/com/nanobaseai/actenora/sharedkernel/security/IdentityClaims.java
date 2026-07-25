package com.nanobaseai.actenora.sharedkernel.security;

import java.util.Objects;
import java.util.Optional;

/**
 * Normalized claims from an identity provider (Entra JWT or local mock).
 * Does not include Actenora tenant id — that is resolved via Tenant membership.
 */
public record IdentityClaims(
        String entraObjectId,
        String entraTenantId,
        String email,
        String displayName,
        boolean globalAdminHint
) {

    public IdentityClaims {
        Objects.requireNonNull(entraObjectId, "entraObjectId");
        Objects.requireNonNull(entraTenantId, "entraTenantId");
        email = email == null ? "" : email;
        displayName = displayName == null || displayName.isBlank() ? email : displayName;
    }

    public Optional<String> emailOptional() {
        return email.isBlank() ? Optional.empty() : Optional.of(email);
    }
}
