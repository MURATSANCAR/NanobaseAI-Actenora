package com.nanobaseai.actenora.aiprocessing.application.port;

import java.util.Objects;
import java.util.UUID;

/**
 * Selects the provider that serves a routed deployment.
 */
public interface LocalModelProviderLocator {

    LocalModelProvider providerFor(UUID deploymentId);

    /** Single-runtime deployments (one local serving process behind every deployment). */
    static LocalModelProviderLocator single(LocalModelProvider provider) {
        Objects.requireNonNull(provider, "provider");
        return deploymentId -> provider;
    }
}
