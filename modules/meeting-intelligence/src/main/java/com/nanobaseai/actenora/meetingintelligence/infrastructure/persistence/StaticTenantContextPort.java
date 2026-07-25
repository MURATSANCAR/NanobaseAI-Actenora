package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.TenantContextPort;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Objects;
import java.util.UUID;

public final class StaticTenantContextPort implements TenantContextPort {

    private final TenantId tenantId;
    private final UUID actorUserId;

    public StaticTenantContextPort(TenantId tenantId, UUID actorUserId) {
        this.tenantId = Objects.requireNonNull(tenantId);
        this.actorUserId = Objects.requireNonNull(actorUserId);
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
