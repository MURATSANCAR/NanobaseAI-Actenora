package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.meetingintelligence.application.port.TenantContextPort;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;

import java.util.UUID;

/**
 * Binds Meeting Intelligence tenant/actor resolution to {@link TenantSecurityContext}.
 */
public final class SecurityContextMeetingIntelligenceTenantPort implements TenantContextPort {

    @Override
    public TenantId requireTenantId() {
        return TenantSecurityContext.require().tenantId();
    }

    @Override
    public UUID requireActorUserId() {
        return TenantSecurityContext.require().userId();
    }
}
