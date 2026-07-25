package com.nanobaseai.actenora.meeting.infrastructure.persistence;

import com.nanobaseai.actenora.meeting.application.port.MeetingParticipantRepository;
import com.nanobaseai.actenora.meeting.domain.model.MeetingParticipant;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryMeetingParticipantRepository implements MeetingParticipantRepository {

    private final Map<UUID, MeetingParticipant> store = new ConcurrentHashMap<>();

    @Override
    public MeetingParticipant save(MeetingParticipant participant) {
        store.put(participant.id(), participant);
        return participant;
    }

    @Override
    public List<MeetingParticipant> findByMeetingOccurrenceIdAndTenantId(UUID meetingOccurrenceId, TenantId tenantId) {
        return store.values().stream()
                .filter(p -> p.meetingOccurrenceId().equals(meetingOccurrenceId))
                .filter(p -> p.tenantId().equals(tenantId))
                .toList();
    }

    public void clear() {
        store.clear();
    }
}
