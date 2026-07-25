package com.nanobaseai.actenora.policy.application;

import com.nanobaseai.actenora.policy.domain.TenantPolicy;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Optional;

/** Optional read-through cache. PostgreSQL remains source of truth. */
public interface PolicyCachePort {
    Optional<TenantPolicy> get(TenantId tenantId);
    void put(TenantId tenantId, TenantPolicy policy);
    void evict(TenantId tenantId);
    void clear();
}
