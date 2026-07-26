package com.nanobaseai.actenora.security.portal;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory portal preferences until tenant settings are persisted in domain storage.
 */
@Component
public class PortalTeamsPreferencesStore {

    private final ConcurrentHashMap<UUID, Boolean> autoJoinByTenant = new ConcurrentHashMap<>();

    public boolean autoJoinEnabled(UUID tenantId) {
        return autoJoinByTenant.getOrDefault(tenantId, false);
    }

    public void setAutoJoinEnabled(UUID tenantId, boolean enabled) {
        autoJoinByTenant.put(tenantId, enabled);
    }
}
