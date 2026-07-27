package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.ProposalRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Proposal;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryProposalRepository implements ProposalRepository {

    private final Map<UUID, Proposal> byId = new ConcurrentHashMap<>();

    @Override
    public Proposal save(Proposal entity) {
        byId.put(entity.id(), entity);
        return entity;
    }

    @Override
    public Optional<Proposal> findByIdAndTenantId(UUID id, TenantId tenantId) {
        return Optional.ofNullable(byId.get(id)).filter(e -> e.tenantId().equals(tenantId));
    }

    @Override
    public List<Proposal> findByNoteId(UUID noteId, TenantId tenantId) {
        return byId.values().stream()
                .filter(e -> e.tenantId().equals(tenantId) && e.noteId().equals(noteId))
                .toList();
    }
}
