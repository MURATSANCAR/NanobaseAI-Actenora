package com.nanobaseai.actenora.tenant.application.port;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.tenant.domain.Tenant;
import com.nanobaseai.actenora.tenant.domain.TenantMembership;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepositoryPort {

    Optional<Tenant> findById(TenantId tenantId);

    Optional<Tenant> findByEntraTenantId(String entraTenantId);

    void save(Tenant tenant);

    boolean isMember(TenantId tenantId, UUID userId);

    void saveMembership(TenantMembership membership);
}
