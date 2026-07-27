package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.IssueRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Issue;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryIssueRepository implements IssueRepository {

    private final Map<UUID, Issue> byId = new ConcurrentHashMap<>();

    @Override
    public Issue save(Issue entity) {
        byId.put(entity.id(), entity);
        return entity;
    }

    @Override
    public Optional<Issue> findByIdAndTenantId(UUID id, TenantId tenantId) {
        return Optional.ofNullable(byId.get(id)).filter(e -> e.tenantId().equals(tenantId));
    }

    @Override
    public List<Issue> findByNoteId(UUID noteId, TenantId tenantId) {
        return byId.values().stream()
                .filter(e -> e.tenantId().equals(tenantId) && e.noteId().equals(noteId))
                .toList();
    }
}
