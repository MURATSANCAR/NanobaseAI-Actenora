package com.nanobaseai.actenora.policy.infrastructure.persistence;

import com.nanobaseai.actenora.policy.application.TenantPolicyRepositoryPort;
import com.nanobaseai.actenora.policy.domain.TenantPolicy;
import com.nanobaseai.actenora.policy.domain.TenantPolicyOverride;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory stand-in for the PostgreSQL policy store (source of truth in production). */
public final class InMemoryTenantPolicyRepository implements TenantPolicyRepositoryPort {

    private final Map<TenantId, TenantPolicyOverride> overrides = new ConcurrentHashMap<>();
    private final Map<TenantId, TenantPolicy> materialized = new ConcurrentHashMap<>();

    @Override
    public Optional<TenantPolicyOverride> findOverride(TenantId tenantId) {
        return Optional.ofNullable(overrides.get(tenantId));
    }

    @Override
    public void saveOverride(TenantPolicyOverride override) {
        overrides.put(override.tenantId(), override);
    }

    @Override
    public Optional<TenantPolicy> findMaterialized(TenantId tenantId) {
        return Optional.ofNullable(materialized.get(tenantId));
    }

    @Override
    public void saveMaterialized(TenantPolicy policy) {
        materialized.put(policy.tenantId(), policy);
    }

    public void clear() {
        overrides.clear();
        materialized.clear();
    }
}
