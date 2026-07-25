package com.nanobaseai.actenora.meeting.infrastructure.tenancy;

import com.nanobaseai.actenora.meeting.application.port.TenantContextPort;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;

import java.util.Objects;
import java.util.UUID;

/**
 * Tenant/actor holder. Prefers {@link TenantSecurityContext} when authenticated identity
 * is bound (FAZ 4). Falls back to explicit {@link #use} values for Teams meeting-app tokens
 * and local/test wiring.
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
        return TenantSecurityContext.current()
                .map(principal -> principal.tenantId())
                .orElse(tenantId);
    }

    @Override
    public UUID requireActorUserId() {
        return TenantSecurityContext.current()
                .map(principal -> principal.userId())
                .orElse(actorUserId);
    }
}
