package com.nanobaseai.actenora.microsoftconnection.infrastructure.config;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * Runtime configuration for Microsoft Graph integration.
 */
public record MicrosoftGraphProperties(
        URI graphBaseUrl,
        URI authorityHost,
        String tenantId,
        String clientId,
        String scope,
        AuthMode authMode,
        Optional<String> clientSecret,
        Optional<String> certificatePemPath,
        Optional<String> privateKeyPemPath,
        Duration subscriptionRenewBefore,
        Duration subscriptionRenewWindow,
        boolean workersEnabled
) {

    public enum AuthMode {
        CERTIFICATE,
        CLIENT_SECRET
    }

    public static MicrosoftGraphProperties localDefaults(URI graphBaseUrl, URI authorityHost) {
        return new MicrosoftGraphProperties(
                graphBaseUrl,
                authorityHost,
                "test-tenant",
                "test-client",
                "https://graph.microsoft.com/.default",
                AuthMode.CLIENT_SECRET,
                Optional.of("local-only-secret"),
                Optional.empty(),
                Optional.empty(),
                Duration.ofHours(6),
                Duration.ofHours(48),
                true
        );
    }
}
