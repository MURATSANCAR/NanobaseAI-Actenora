package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.TopicRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Topic;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryTopicRepository implements TopicRepository {

    private final Map<UUID, Topic> byId = new ConcurrentHashMap<>();

    @Override
    public Topic save(Topic entity) {
        byId.put(entity.id(), entity);
        return entity;
    }

    @Override
    public Optional<Topic> findByIdAndTenantId(UUID id, TenantId tenantId) {
        return Optional.ofNullable(byId.get(id)).filter(e -> e.tenantId().equals(tenantId));
    }

    @Override
    public List<Topic> findByNoteId(UUID noteId, TenantId tenantId) {
        return byId.values().stream()
                .filter(e -> e.tenantId().equals(tenantId) && e.noteId().equals(noteId))
                .toList();
    }
}
