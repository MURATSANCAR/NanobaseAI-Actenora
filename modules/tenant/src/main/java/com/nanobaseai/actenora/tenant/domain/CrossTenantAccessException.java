package com.nanobaseai.actenora.tenant.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

public final class CrossTenantAccessException extends RuntimeException {

    private final TenantId principalTenantId;
    private final TenantId resourceTenantId;

    public CrossTenantAccessException(TenantId principalTenantId, TenantId resourceTenantId) {
        super("Cross-tenant access denied: principal="
                + principalTenantId.value()
                + " resource="
                + resourceTenantId.value());
        this.principalTenantId = principalTenantId;
        this.resourceTenantId = resourceTenantId;
    }

    public TenantId principalTenantId() {
        return principalTenantId;
    }

    public TenantId resourceTenantId() {
        return resourceTenantId;
    }
}
