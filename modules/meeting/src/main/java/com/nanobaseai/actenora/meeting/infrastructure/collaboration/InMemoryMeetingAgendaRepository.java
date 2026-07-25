package com.nanobaseai.actenora.meeting.infrastructure.collaboration;

import com.nanobaseai.actenora.meeting.application.collaboration.port.MeetingAgendaRepository;
import com.nanobaseai.actenora.meeting.domain.collaboration.MeetingAgenda;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryMeetingAgendaRepository implements MeetingAgendaRepository {

    private final Map<UUID, MeetingAgenda> store = new ConcurrentHashMap<>();

    @Override
    public MeetingAgenda save(MeetingAgenda agenda) {
        store.put(agenda.id(), agenda);
        return agenda;
    }

    @Override
    public Optional<MeetingAgenda> findByMeetingOccurrenceIdAndTenantId(UUID meetingOccurrenceId, TenantId tenantId) {
        return store.values().stream()
                .filter(a -> a.meetingOccurrenceId().equals(meetingOccurrenceId))
                .filter(a -> a.tenantId().equals(tenantId))
                .findFirst();
    }

    @Override
    public Optional<MeetingAgenda> findByIdAndTenantId(UUID id, TenantId tenantId) {
        return Optional.ofNullable(store.get(id)).filter(a -> a.tenantId().equals(tenantId));
    }

    public void clear() {
        store.clear();
    }
}
