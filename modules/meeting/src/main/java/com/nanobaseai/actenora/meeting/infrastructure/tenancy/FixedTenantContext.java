package com.nanobaseai.actenora.meeting.infrastructure.tenancy;

import com.nanobaseai.actenora.meeting.application.port.TenantContextPort;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Objects;
import java.util.UUID;

/**
 * Explicit tenant/actor holder for tests and local wiring.
 * Production resolves from authenticated identity (FAZ 4).
 */
public final class FixedTenantContext implements TenantContextPort {

    private volatile TenantId tenantId;
    private volatile UUID actorUserId;

    public FixedTenantContext(TenantId tenantId, UUID actorUserId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
    }

    public void use(TenantId tenantId, UUID actorUserId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
    }

    @Override
    public TenantId requireTenantId() {
        return tenantId;
    }

    @Override
    public UUID requireActorUserId() {
        return actorUserId;
    }
}
