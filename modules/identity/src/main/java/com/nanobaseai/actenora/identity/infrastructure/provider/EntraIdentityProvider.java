package com.nanobaseai.actenora.identity.infrastructure.provider;

import com.nanobaseai.actenora.identity.application.port.IdentityProviderPort;
import com.nanobaseai.actenora.identity.application.port.IdentityProviderRequest;
import com.nanobaseai.actenora.sharedkernel.security.IdentityClaims;

import java.util.Optional;

/**
 * Maps Microsoft Entra ID (Azure AD) JWT claims to {@link IdentityClaims}.
 * Expected claims: {@code oid} (or {@code sub}), {@code tid}, {@code preferred_username}/{@code email}, {@code name}.
 */
public final class EntraIdentityProvider implements IdentityProviderPort {

    public static final String CLAIM_OID = "oid";
    public static final String CLAIM_SUB = "sub";
    public static final String CLAIM_TID = "tid";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_PREFERRED_USERNAME = "preferred_username";
    public static final String CLAIM_NAME = "name";
    public static final String CLAIM_ROLES = "roles";

    @Override
    public Optional<IdentityClaims> extractClaims(IdentityProviderRequest request) {
        Optional<String> oid = stringClaim(request, CLAIM_OID).or(() -> stringClaim(request, CLAIM_SUB));
        Optional<String> tid = stringClaim(request, CLAIM_TID);
        if (oid.isEmpty() || tid.isEmpty()) {
            return Optional.empty();
        }
        String email = stringClaim(request, CLAIM_EMAIL)
                .or(() -> stringClaim(request, CLAIM_PREFERRED_USERNAME))
                .orElse("");
        String displayName = stringClaim(request, CLAIM_NAME).orElse(email);
        boolean globalAdmin = hasRole(request, "SUPER_ADMIN") || hasRole(request, "GlobalAdministrator");
        return Optional.of(new IdentityClaims(oid.get(), tid.get(), email, displayName, globalAdmin));
    }

    private static Optional<String> stringClaim(IdentityProviderRequest request, String name) {
        return request.claim(name).map(Object::toString).filter(value -> !value.isBlank());
    }

    private static boolean hasRole(IdentityProviderRequest request, String role) {
        Object roles = request.jwtClaims().get(CLAIM_ROLES);
        if (roles instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (role.equals(String.valueOf(item))) {
                    return true;
                }
            }
        }
        return false;
    }
}
