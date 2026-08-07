package com.nanobaseai.actenora.microsoftconnection.infrastructure.graph;

import java.net.URI;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Holds the current Microsoft Graph base URL behind a volatile reference so it can be
 * re-pointed at runtime by the admin-editable connection screen without rebuilding the
 * {@link GraphHttpClient} or the gateways that depend on it.
 */
public final class MutableGraphEndpoint implements Supplier<URI> {

    private volatile URI baseUrl;

    public MutableGraphEndpoint(URI initial) {
        this.baseUrl = Objects.requireNonNull(initial, "initial");
    }

    @Override
    public URI get() {
        return baseUrl;
    }

    public void set(URI baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
    }
}
