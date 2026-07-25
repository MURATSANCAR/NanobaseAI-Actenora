package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingNoteRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNote;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryMeetingNoteRepository implements MeetingNoteRepository {

    private final Map<UUID, MeetingNote> byId = new ConcurrentHashMap<>();

    @Override
    public MeetingNote save(MeetingNote note) {
        byId.put(note.id(), note);
        return note;
    }

    @Override
    public Optional<MeetingNote> findByIdAndTenantId(UUID id, TenantId tenantId) {
        return Optional.ofNullable(byId.get(id)).filter(n -> n.tenantId().equals(tenantId));
    }
}
