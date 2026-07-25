package com.nanobaseai.actenora.meeting.infrastructure.collaboration;

import com.nanobaseai.actenora.meeting.application.collaboration.port.CollaborationIdempotencyStore;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryCollaborationIdempotencyStore implements CollaborationIdempotencyStore {

    private final Map<String, String> store = new ConcurrentHashMap<>();

    @Override
    public Optional<String> findResponseJson(TenantId tenantId, UUID actorUserId, String idempotencyKey) {
        return Optional.ofNullable(store.get(key(tenantId, actorUserId, idempotencyKey)));
    }

    @Override
    public void putResponseJson(TenantId tenantId, UUID actorUserId, String idempotencyKey, String responseJson) {
        store.put(key(tenantId, actorUserId, idempotencyKey), Objects.requireNonNull(responseJson));
    }

    private static String key(TenantId tenantId, UUID actorUserId, String idempotencyKey) {
        return tenantId.value() + ":" + actorUserId + ":" + idempotencyKey;
    }

    public void clear() {
        store.clear();
    }
}
