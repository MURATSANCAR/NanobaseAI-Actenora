package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.ImportantFactRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ImportantFact;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryImportantFactRepository implements ImportantFactRepository {

    private final Map<UUID, ImportantFact> byId = new ConcurrentHashMap<>();

    @Override
    public ImportantFact save(ImportantFact entity) {
        byId.put(entity.id(), entity);
        return entity;
    }

    @Override
    public Optional<ImportantFact> findByIdAndTenantId(UUID id, TenantId tenantId) {
        return Optional.ofNullable(byId.get(id)).filter(e -> e.tenantId().equals(tenantId));
    }

    @Override
    public List<ImportantFact> findByNoteId(UUID noteId, TenantId tenantId) {
        return byId.values().stream()
                .filter(e -> e.tenantId().equals(tenantId) && e.noteId().equals(noteId))
                .toList();
    }
}
