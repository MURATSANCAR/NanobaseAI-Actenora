package com.nanobaseai.actenora.meeting.application.collaboration.port;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * Stores responses for Idempotency-Key scoped per tenant + actor.
 */
public interface CollaborationIdempotencyStore {

    Optional<String> findResponseJson(TenantId tenantId, UUID actorUserId, String idempotencyKey);

    void putResponseJson(TenantId tenantId, UUID actorUserId, String idempotencyKey, String responseJson);
}
