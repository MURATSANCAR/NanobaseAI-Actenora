package com.nanobaseai.actenora.tenant.api;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * Public façade for the Tenant bounded context.
 * Cross-module callers use types in this package only.
 */
public interface TenantApi {

    TenantView requireActive(TenantId tenantId);

    Optional<TenantView> findById(TenantId tenantId);

    Optional<TenantView> findByEntraTenantId(String entraTenantId);

    TenantView provision(
            String name,
            String entraTenantId,
            String timezone,
            String defaultLanguage,
            int retentionPolicyDays
    );

    TenantView suspend(TenantId tenantId, long expectedVersion);

    TenantView activate(TenantId tenantId, long expectedVersion);

    boolean isMember(TenantId tenantId, UUID userId);

    void ensureMembership(TenantId tenantId, UUID userId);

    void assertSameTenant(TenantId principalTenantId, TenantId resourceTenantId);
}
