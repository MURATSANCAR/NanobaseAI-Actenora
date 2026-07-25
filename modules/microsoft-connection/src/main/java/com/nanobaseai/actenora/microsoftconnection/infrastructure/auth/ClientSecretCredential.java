package com.nanobaseai.actenora.microsoftconnection.infrastructure.auth;

import java.util.Objects;

/**
 * Optional client-secret credential for local / integration tests only.
 */
public record ClientSecretCredential(
        String tenantId,
        String clientId,
        String clientSecret,
        String authorityHost,
        String scope
) {

    public ClientSecretCredential {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(clientSecret, "clientSecret");
        Objects.requireNonNull(authorityHost, "authorityHost");
        Objects.requireNonNull(scope, "scope");
    }

    public String tokenEndpoint() {
        String host = authorityHost.endsWith("/")
                ? authorityHost.substring(0, authorityHost.length() - 1)
                : authorityHost;
        return host + "/" + tenantId + "/oauth2/v2.0/token";
    }
}
