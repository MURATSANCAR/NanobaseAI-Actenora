package com.nanobaseai.actenora.approval.infrastructure;

import com.nanobaseai.actenora.approval.application.port.ParticipantDisputeRepository;
import com.nanobaseai.actenora.approval.domain.ParticipantDispute;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryParticipantDisputeRepository implements ParticipantDisputeRepository {

    private final Map<UUID, ParticipantDispute> byId = new ConcurrentHashMap<>();

    @Override
    public ParticipantDispute save(ParticipantDispute dispute) {
        byId.put(dispute.id(), dispute);
        return dispute;
    }

    @Override
    public Optional<ParticipantDispute> findById(UUID tenantId, UUID disputeId) {
        return Optional.ofNullable(byId.get(disputeId))
                .filter(d -> d.tenantId().equals(tenantId));
    }

    @Override
    public List<ParticipantDispute> findBySubject(UUID tenantId, UUID subjectId) {
        return byId.values().stream()
                .filter(d -> d.tenantId().equals(tenantId))
                .filter(d -> d.subjectId().equals(subjectId))
                .toList();
    }
}
