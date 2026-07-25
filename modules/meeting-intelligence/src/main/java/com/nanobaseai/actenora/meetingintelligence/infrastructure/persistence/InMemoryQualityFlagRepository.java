package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.QualityFlagRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.model.QualityFlag;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryQualityFlagRepository implements QualityFlagRepository {

    private final Map<UUID, QualityFlag> byId = new ConcurrentHashMap<>();

    @Override
    public QualityFlag save(QualityFlag flag) {
        byId.put(flag.id(), flag);
        return flag;
    }

    @Override
    public List<QualityFlag> findByNoteId(UUID noteId, TenantId tenantId) {
        return byId.values().stream()
                .filter(e -> e.tenantId().equals(tenantId) && e.noteId().equals(noteId))
                .toList();
    }
}
