package com.nanobaseai.actenora.aiprocessing.application.modelworker;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Declared runtime capabilities for a local provider adapter.
 */
public record ProviderCapabilities(
        String providerKind,
        boolean streamingSupported,
        boolean cancellationSupported,
        int maxConcurrency,
        Set<String> servedModelIds
) {
    public ProviderCapabilities {
        Objects.requireNonNull(providerKind, "providerKind");
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("maxConcurrency must be >= 1");
        }
        servedModelIds = Collections.unmodifiableSet(
                new LinkedHashSet<>(servedModelIds == null ? Set.of() : servedModelIds));
    }

    public boolean supportsServedModel(String servedModelId) {
        if (servedModelIds.isEmpty()) {
            return true;
        }
        return servedModelIds.contains(servedModelId);
    }
}
