package com.nanobaseai.actenora.identity.infrastructure.provider;

import com.nanobaseai.actenora.identity.application.port.IdentityProviderPort;
import com.nanobaseai.actenora.identity.application.port.IdentityProviderRequest;
import com.nanobaseai.actenora.sharedkernel.security.IdentityClaims;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Operator identity via request headers (real Entra oid/tid/email).
 * Must never be enabled on the strict production profile — use Entra JWT instead.
 */
public final class MockIdentityProvider implements IdentityProviderPort {

    public static final String HEADER_OID = "X-Actenora-Entra-Oid";
    public static final String HEADER_TID = "X-Actenora-Entra-Tid";
    public static final String HEADER_EMAIL = "X-Actenora-Email";
    public static final String HEADER_NAME = "X-Actenora-Display-Name";
    public static final String HEADER_GLOBAL_ADMIN = "X-Actenora-Global-Admin";

    @Override
    public Optional<IdentityClaims> extractClaims(IdentityProviderRequest request) {
        Optional<String> oid = headerIgnoreCase(request.headers(), HEADER_OID);
        Optional<String> tid = headerIgnoreCase(request.headers(), HEADER_TID);
        Optional<String> email = headerIgnoreCase(request.headers(), HEADER_EMAIL);
        Optional<String> name = headerIgnoreCase(request.headers(), HEADER_NAME);
        if (oid.isEmpty() || tid.isEmpty() || email.isEmpty() || name.isEmpty()) {
            return Optional.empty();
        }
        boolean globalAdmin = headerIgnoreCase(request.headers(), HEADER_GLOBAL_ADMIN)
                .map(value -> "true".equalsIgnoreCase(value) || "1".equals(value))
                .orElse(false);
        return Optional.of(new IdentityClaims(oid.get(), tid.get(), email.get(), name.get(), globalAdmin));
    }

    private static Optional<String> headerIgnoreCase(Map<String, String> headers, String name) {
        String needle = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().toLowerCase(Locale.ROOT).equals(needle)) {
                String value = entry.getValue();
                if (value != null && !value.isBlank()) {
                    return Optional.of(value.trim());
                }
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
