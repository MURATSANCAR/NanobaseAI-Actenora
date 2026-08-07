package com.nanobaseai.actenora.security.microsoftconnection;

import java.util.Optional;

/**
 * Persistence for the single, deployment-wide Microsoft Graph connection settings row.
 * Implementations encrypt the client secret at rest.
 */
public interface GraphConnectionSettingsStore {

    Optional<GraphConnectionSettings> load();

    void save(GraphConnectionSettings settings);
}
