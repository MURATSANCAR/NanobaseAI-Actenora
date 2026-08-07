package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.microsoftconnection.infrastructure.config.MicrosoftGraphProperties;

import java.util.Objects;
import java.util.Optional;

/**
 * Editable subset of the Microsoft Graph / Teams connection managed from the admin portal.
 * The {@code clientSecret} is held in plaintext in memory only; persistence encrypts it at rest.
 */
public record GraphConnectionSettings(
        boolean enabled,
        String graphBaseUrl,
        String authorityHost,
        String tenantId,
        String clientId,
        String scope,
        MicrosoftGraphProperties.AuthMode authMode,
        Optional<String> clientSecret,
        Optional<String> certificatePemPath,
        Optional<String> privateKeyPemPath,
        Optional<String> defaultMailboxUserId
) {

    public GraphConnectionSettings {
        Objects.requireNonNull(graphBaseUrl, "graphBaseUrl");
        Objects.requireNonNull(authorityHost, "authorityHost");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(authMode, "authMode");
        clientSecret = normalize(clientSecret);
        certificatePemPath = normalize(certificatePemPath);
        privateKeyPemPath = normalize(privateKeyPemPath);
        defaultMailboxUserId = normalize(defaultMailboxUserId);
    }

    private static Optional<String> normalize(Optional<String> value) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        String trimmed = value.get() == null ? "" : value.get().trim();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
    }

    public boolean secretConfigured() {
        return clientSecret.isPresent();
    }
}
