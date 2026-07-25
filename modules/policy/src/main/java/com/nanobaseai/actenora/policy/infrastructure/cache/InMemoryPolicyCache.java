package com.nanobaseai.actenora.policy.infrastructure.cache;

import com.nanobaseai.actenora.policy.application.PolicyCachePort;
import com.nanobaseai.actenora.policy.domain.TenantPolicy;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Process-local policy cache. Loss must not change evaluation results (SoT reload). */
public final class InMemoryPolicyCache implements PolicyCachePort {

    private final Map<TenantId, TenantPolicy> entries = new ConcurrentHashMap<>();

    @Override
    public Optional<TenantPolicy> get(TenantId tenantId) {
        return Optional.ofNullable(entries.get(tenantId));
    }

    @Override
    public void put(TenantId tenantId, TenantPolicy policy) {
        entries.put(tenantId, policy);
    }

    @Override
    public void evict(TenantId tenantId) {
        entries.remove(tenantId);
    }

    @Override
    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }
}
