package com.nanobaseai.actenora.tenant.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Objects;
import java.util.UUID;

public record TenantMembership(TenantId tenantId, UUID userId) {

    public TenantMembership {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
    }
}
