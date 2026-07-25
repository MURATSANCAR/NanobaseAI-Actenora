package com.nanobaseai.actenora.meeting.infrastructure.collaboration;

import com.nanobaseai.actenora.meeting.application.collaboration.port.PrivateNoteRepository;
import com.nanobaseai.actenora.meeting.domain.collaboration.PrivateNote;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryPrivateNoteRepository implements PrivateNoteRepository {

    private final Map<UUID, PrivateNote> store = new ConcurrentHashMap<>();

    @Override
    public PrivateNote save(PrivateNote note) {
        store.put(note.id(), note);
        return note;
    }

    @Override
    public Optional<PrivateNote> findByIdAndTenantId(UUID id, TenantId tenantId) {
        return Optional.ofNullable(store.get(id)).filter(n -> n.tenantId().equals(tenantId));
    }

    @Override
    public Optional<PrivateNote> findByMeetingOccurrenceIdAndOwnerAndTenantId(
            UUID meetingOccurrenceId,
            UUID ownerUserId,
            TenantId tenantId
    ) {
        return store.values().stream()
                .filter(n -> n.meetingOccurrenceId().equals(meetingOccurrenceId))
                .filter(n -> n.ownerUserId().equals(ownerUserId))
                .filter(n -> n.tenantId().equals(tenantId))
                .findFirst();
    }

    @Override
    public List<PrivateNote> findByMeetingOccurrenceIdAndTenantId(UUID meetingOccurrenceId, TenantId tenantId) {
        return store.values().stream()
                .filter(n -> n.meetingOccurrenceId().equals(meetingOccurrenceId))
                .filter(n -> n.tenantId().equals(tenantId))
                .toList();
    }

    @Override
    public void delete(UUID id, TenantId tenantId) {
        Optional<PrivateNote> existing = findByIdAndTenantId(id, tenantId);
        existing.ifPresent(note -> store.remove(note.id()));
    }

    @Override
    public List<PrivateNote> findAllByTenantId(TenantId tenantId) {
        return store.values().stream()
                .filter(n -> n.tenantId().equals(tenantId))
                .toList();
    }

    public void clear() {
        store.clear();
    }
}
