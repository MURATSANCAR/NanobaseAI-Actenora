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

    private final boolean requireSecurityContext;
    private volatile TenantId tenantId;
    private volatile UUID actorUserId;

    public FixedTenantContext(TenantId tenantId, UUID actorUserId) {
        this(tenantId, actorUserId, false);
    }

    public FixedTenantContext(TenantId tenantId, UUID actorUserId, boolean requireSecurityContext) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
        this.requireSecurityContext = requireSecurityContext;
    }

    public void use(TenantId tenantId, UUID actorUserId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
    }

    @Override
    public TenantId requireTenantId() {
        if (requireSecurityContext) {
            return TenantSecurityContext.requireTenantId();
        }
        return TenantSecurityContext.current()
                .map(principal -> principal.tenantId())
                .orElse(tenantId);
    }

    @Override
    public UUID requireActorUserId() {
        if (requireSecurityContext) {
            return TenantSecurityContext.requireUserId();
        }
        return TenantSecurityContext.current()
                .map(principal -> principal.userId())
                .orElse(actorUserId);
    }
}
