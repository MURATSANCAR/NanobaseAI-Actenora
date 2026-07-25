package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.EvidenceLinkRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.model.EvidenceLink;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryEvidenceLinkRepository implements EvidenceLinkRepository {

    private final Map<UUID, EvidenceLink> byId = new ConcurrentHashMap<>();

    @Override
    public EvidenceLink save(EvidenceLink link) {
        byId.put(link.id(), link);
        return link;
    }

    @Override
    public Optional<EvidenceLink> findByIdAndTenantId(UUID id, TenantId tenantId) {
        return Optional.ofNullable(byId.get(id)).filter(e -> e.tenantId().equals(tenantId));
    }

    @Override
    public List<EvidenceLink> findByNoteId(UUID noteId, TenantId tenantId) {
        return byId.values().stream()
                .filter(e -> e.tenantId().equals(tenantId) && e.noteId().equals(noteId))
                .toList();
    }

    @Override
    public List<EvidenceLink> findBySubjectId(UUID subjectId, TenantId tenantId) {
        return byId.values().stream()
                .filter(e -> e.tenantId().equals(tenantId) && e.subjectId().equals(subjectId))
                .toList();
    }
}
