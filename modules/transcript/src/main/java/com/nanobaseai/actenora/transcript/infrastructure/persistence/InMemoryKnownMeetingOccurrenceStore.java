package com.nanobaseai.actenora.transcript.infrastructure.persistence;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.application.port.out.KnownMeetingOccurrenceStore;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryKnownMeetingOccurrenceStore implements KnownMeetingOccurrenceStore {

    private final Set<String> keys = ConcurrentHashMap.newKeySet();

    @Override
    public void remember(TenantId tenantId, UUID meetingOccurrenceId) {
        keys.add(key(tenantId, meetingOccurrenceId));
    }

    @Override
    public boolean isKnown(TenantId tenantId, UUID meetingOccurrenceId) {
        return keys.contains(key(tenantId, meetingOccurrenceId));
    }

    private static String key(TenantId tenantId, UUID meetingOccurrenceId) {
        return tenantId.value() + ":" + meetingOccurrenceId;
    }
}
