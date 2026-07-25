package com.nanobaseai.actenora.identity.application.port;

import java.util.Map;
import java.util.Optional;

/**
 * Provider-neutral request payload (JWT claims map or mock headers).
 */
public record IdentityProviderRequest(
        Map<String, Object> jwtClaims,
        Map<String, String> headers
) {

    public IdentityProviderRequest {
        jwtClaims = jwtClaims == null ? Map.of() : Map.copyOf(jwtClaims);
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public Optional<Object> claim(String name) {
        return Optional.ofNullable(jwtClaims.get(name));
    }

    public Optional<String> header(String name) {
        return Optional.ofNullable(headers.get(name));
    }
}
