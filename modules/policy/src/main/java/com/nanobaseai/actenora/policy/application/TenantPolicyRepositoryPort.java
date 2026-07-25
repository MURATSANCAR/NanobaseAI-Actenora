package com.nanobaseai.actenora.policy.application;

import com.nanobaseai.actenora.policy.domain.TenantPolicy;
import com.nanobaseai.actenora.policy.domain.TenantPolicyOverride;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Optional;

/** Source-of-truth persistence port for tenant policy overrides (PostgreSQL). */
public interface TenantPolicyRepositoryPort {
    Optional<TenantPolicyOverride> findOverride(TenantId tenantId);
    void saveOverride(TenantPolicyOverride override);
    Optional<TenantPolicy> findMaterialized(TenantId tenantId);
    void saveMaterialized(TenantPolicy policy);
}
