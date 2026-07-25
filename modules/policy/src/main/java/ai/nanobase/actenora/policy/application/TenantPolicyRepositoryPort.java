package ai.nanobase.actenora.policy.application;

import ai.nanobase.actenora.policy.domain.TenantPolicy;
import ai.nanobase.actenora.policy.domain.TenantPolicyOverride;

import java.util.Optional;
import java.util.UUID;

/**
 * Source-of-truth persistence port for tenant policy overrides.
 * Implementations must read/write PostgreSQL (or an equivalent durable store in tests).
 */
public interface TenantPolicyRepositoryPort {

    Optional<TenantPolicyOverride> findOverride(UUID tenantId);

    void saveOverride(TenantPolicyOverride override);

    /**
     * Optional materialized effective policy used only as a write-through cache of SoT merges.
     * May be empty; callers must always be able to resolve from defaults + override.
     */
    Optional<TenantPolicy> findMaterialized(UUID tenantId);

    void saveMaterialized(TenantPolicy policy);
}
