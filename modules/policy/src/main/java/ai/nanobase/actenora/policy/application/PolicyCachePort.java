package ai.nanobase.actenora.policy.application;

import ai.nanobase.actenora.policy.domain.TenantPolicy;

import java.util.Optional;
import java.util.UUID;

/**
 * Optional read-through cache. PostgreSQL remains source of truth.
 */
public interface PolicyCachePort {

    Optional<TenantPolicy> get(UUID tenantId);

    void put(UUID tenantId, TenantPolicy policy);

    void evict(UUID tenantId);

    void clear();
}
