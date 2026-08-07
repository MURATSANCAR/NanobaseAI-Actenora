package com.nanobaseai.actenora.security.microsoftconnection;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory settings store for local/dev runtimes ({@code actenora.persistence.mode=inmemory}).
 * Not durable across restarts — startup falls back to boot properties when empty.
 */
public final class InMemoryGraphConnectionSettingsStore implements GraphConnectionSettingsStore {

    private final AtomicReference<GraphConnectionSettings> current = new AtomicReference<>();

    @Override
    public Optional<GraphConnectionSettings> load() {
        return Optional.ofNullable(current.get());
    }

    @Override
    public void save(GraphConnectionSettings settings) {
        current.set(settings);
    }
}
