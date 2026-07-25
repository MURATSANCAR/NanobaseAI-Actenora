package com.nanobaseai.actenora.tenant.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

public final class TenantNotActiveException extends RuntimeException {

    private final TenantId tenantId;
    private final TenantStatus status;

    public TenantNotActiveException(TenantId tenantId, TenantStatus status) {
        super("Tenant " + tenantId.value() + " is not active (status=" + status + ")");
        this.tenantId = tenantId;
        this.status = status;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public TenantStatus status() {
        return status;
    }
}
