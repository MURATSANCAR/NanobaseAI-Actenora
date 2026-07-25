package com.nanobaseai.actenora.meeting.infrastructure.collaboration;

import com.nanobaseai.actenora.meeting.application.collaboration.port.SharedNoteRepository;
import com.nanobaseai.actenora.meeting.domain.collaboration.SharedNote;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemorySharedNoteRepository implements SharedNoteRepository {

    private final Map<UUID, SharedNote> store = new ConcurrentHashMap<>();

    @Override
    public SharedNote save(SharedNote note) {
        store.put(note.id(), note);
        return note;
    }

    @Override
    public Optional<SharedNote> findByMeetingOccurrenceIdAndTenantId(UUID meetingOccurrenceId, TenantId tenantId) {
        return store.values().stream()
                .filter(n -> n.meetingOccurrenceId().equals(meetingOccurrenceId))
                .filter(n -> n.tenantId().equals(tenantId))
                .findFirst();
    }

    @Override
    public Optional<SharedNote> findByIdAndTenantId(UUID id, TenantId tenantId) {
        return Optional.ofNullable(store.get(id)).filter(n -> n.tenantId().equals(tenantId));
    }

    public void clear() {
        store.clear();
    }
}
