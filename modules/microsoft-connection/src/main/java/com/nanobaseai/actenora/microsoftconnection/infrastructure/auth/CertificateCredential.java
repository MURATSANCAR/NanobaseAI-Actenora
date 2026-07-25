package com.nanobaseai.actenora.microsoftconnection.infrastructure.auth;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Objects;

/**
 * Certificate credential material for Microsoft client-assertion auth.
 */
public record CertificateCredential(
        String tenantId,
        String clientId,
        String authorityHost,
        X509Certificate certificate,
        PrivateKey privateKey,
        String scope
) {

    public CertificateCredential {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(authorityHost, "authorityHost");
        Objects.requireNonNull(certificate, "certificate");
        Objects.requireNonNull(privateKey, "privateKey");
        Objects.requireNonNull(scope, "scope");
        if (tenantId.isBlank() || clientId.isBlank()) {
            throw new IllegalArgumentException("tenantId and clientId must not be blank");
        }
    }

    public String tokenEndpoint() {
        String host = authorityHost.endsWith("/")
                ? authorityHost.substring(0, authorityHost.length() - 1)
                : authorityHost;
        return host + "/" + tenantId + "/oauth2/v2.0/token";
    }
}
