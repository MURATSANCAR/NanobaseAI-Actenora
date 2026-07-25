package com.nanobaseai.actenora.meeting.application.port;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.UUID;

/**
 * Resolves tenant and actor from authenticated identity — never from request body.
 */
public interface TenantContextPort {

    TenantId requireTenantId();

    UUID requireActorUserId();
}
