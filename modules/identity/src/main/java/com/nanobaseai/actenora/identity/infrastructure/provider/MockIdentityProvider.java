package com.nanobaseai.actenora.identity.infrastructure.provider;

import com.nanobaseai.actenora.identity.application.port.IdentityProviderPort;
import com.nanobaseai.actenora.identity.application.port.IdentityProviderRequest;
import com.nanobaseai.actenora.sharedkernel.security.IdentityClaims;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Local development identity provider. Reads mock headers only.
 * Must never be enabled on the production profile.
 */
public final class MockIdentityProvider implements IdentityProviderPort {

    public static final String HEADER_OID = "X-Mock-Entra-Oid";
    public static final String HEADER_TID = "X-Mock-Entra-Tid";
    public static final String HEADER_EMAIL = "X-Mock-Email";
    public static final String HEADER_NAME = "X-Mock-Display-Name";
    public static final String HEADER_GLOBAL_ADMIN = "X-Mock-Global-Admin";

    @Override
    public Optional<IdentityClaims> extractClaims(IdentityProviderRequest request) {
        Optional<String> oid = headerIgnoreCase(request.headers(), HEADER_OID);
        Optional<String> tid = headerIgnoreCase(request.headers(), HEADER_TID);
        if (oid.isEmpty() || tid.isEmpty()) {
            return Optional.empty();
        }
        String email = headerIgnoreCase(request.headers(), HEADER_EMAIL).orElse(oid.get() + "@mock.local");
        String name = headerIgnoreCase(request.headers(), HEADER_NAME).orElse(email);
        boolean globalAdmin = headerIgnoreCase(request.headers(), HEADER_GLOBAL_ADMIN)
                .map(value -> "true".equalsIgnoreCase(value) || "1".equals(value))
                .orElse(false);
        return Optional.of(new IdentityClaims(oid.get(), tid.get(), email, name, globalAdmin));
    }

    private static Optional<String> headerIgnoreCase(Map<String, String> headers, String name) {
        String needle = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().toLowerCase(Locale.ROOT).equals(needle)) {
                if (entry.getValue() == null || entry.getValue().isBlank()) {
                    return Optional.empty();
                }
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }
}
