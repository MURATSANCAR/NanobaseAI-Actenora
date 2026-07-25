package com.nanobaseai.actenora.meeting.infrastructure.collaboration;

import com.nanobaseai.actenora.meeting.application.collaboration.port.MeetingMarkerRepository;
import com.nanobaseai.actenora.meeting.domain.collaboration.MeetingMarker;
import com.nanobaseai.actenora.meeting.domain.collaboration.MarkerType;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryMeetingMarkerRepository implements MeetingMarkerRepository {

    private final Map<UUID, MeetingMarker> store = new ConcurrentHashMap<>();

    @Override
    public MeetingMarker save(MeetingMarker marker) {
        store.put(marker.id(), marker);
        return marker;
    }

    @Override
    public Optional<MeetingMarker> findByTenantAndIdempotencyKey(TenantId tenantId, UUID actorUserId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return store.values().stream()
                .filter(m -> m.tenantId().equals(tenantId))
                .filter(m -> m.createdByUserId().equals(actorUserId))
                .filter(m -> idempotencyKey.equals(m.idempotencyKey()))
                .findFirst();
    }

    @Override
    public List<MeetingMarker> findByMeetingOccurrenceIdAndTenantId(UUID meetingOccurrenceId, TenantId tenantId) {
        return store.values().stream()
                .filter(m -> m.meetingOccurrenceId().equals(meetingOccurrenceId))
                .filter(m -> m.tenantId().equals(tenantId))
                .toList();
    }

    @Override
    public List<MeetingMarker> findByMeetingOccurrenceIdAndTenantIdAndType(
            UUID meetingOccurrenceId,
            TenantId tenantId,
            MarkerType type
    ) {
        return findByMeetingOccurrenceIdAndTenantId(meetingOccurrenceId, tenantId).stream()
                .filter(m -> m.type() == type)
                .toList();
    }

    public void clear() {
        store.clear();
    }
}
