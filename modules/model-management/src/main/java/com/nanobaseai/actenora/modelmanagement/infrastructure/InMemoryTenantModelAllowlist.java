package com.nanobaseai.actenora.modelmanagement.infrastructure;

import com.nanobaseai.actenora.modelmanagement.application.TenantModelAllowlistPort;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple allowlist store used until PolicyEvaluationPort is wired in the platform app.
 */
public final class InMemoryTenantModelAllowlist implements TenantModelAllowlistPort {

    private final Map<UUID, Set<String>> allowedByTenant = new ConcurrentHashMap<>();

    public void allow(UUID tenantId, String modelKey) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(modelKey, "modelKey");
        allowedByTenant.computeIfAbsent(tenantId, id -> ConcurrentHashMap.newKeySet()).add(modelKey);
    }

    @Override
    public boolean isModelAllowed(UUID tenantId, String modelKey) {
        Set<String> allowed = allowedByTenant.get(tenantId);
        return allowed != null && allowed.contains(modelKey);
    }
}
