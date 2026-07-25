package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingNoteVersionRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNoteVersion;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryMeetingNoteVersionRepository implements MeetingNoteVersionRepository {

    private final Map<UUID, MeetingNoteVersion> byId = new ConcurrentHashMap<>();

    @Override
    public MeetingNoteVersion save(MeetingNoteVersion version) {
        byId.put(version.id(), version);
        return version;
    }

    @Override
    public Optional<MeetingNoteVersion> findByIdAndTenantId(UUID id, TenantId tenantId) {
        return Optional.ofNullable(byId.get(id)).filter(v -> v.tenantId().equals(tenantId));
    }

    @Override
    public Optional<MeetingNoteVersion> findByNoteIdAndVersionNumber(UUID noteId, int versionNumber, TenantId tenantId) {
        return byId.values().stream()
                .filter(v -> v.tenantId().equals(tenantId))
                .filter(v -> v.noteId().equals(noteId) && v.versionNumber() == versionNumber)
                .findFirst();
    }

    @Override
    public List<MeetingNoteVersion> findAllByNoteId(UUID noteId, TenantId tenantId) {
        return byId.values().stream()
                .filter(v -> v.tenantId().equals(tenantId) && v.noteId().equals(noteId))
                .sorted(Comparator.comparingInt(MeetingNoteVersion::versionNumber))
                .toList();
    }
}
