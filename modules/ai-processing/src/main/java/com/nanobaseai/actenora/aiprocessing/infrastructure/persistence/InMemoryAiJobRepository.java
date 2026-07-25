package com.nanobaseai.actenora.aiprocessing.infrastructure.persistence;

import com.nanobaseai.actenora.aiprocessing.application.port.AiJobRepository;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryAiJobRepository implements AiJobRepository {

    private final Map<UUID, AiJob> store = new ConcurrentHashMap<>();

    @Override
    public void save(AiJob job) {
        store.put(job.id(), job);
    }

    @Override
    public Optional<AiJob> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<AiJob> findDuplicate(
            UUID tenantId,
            UUID meetingOccurrenceId,
            UUID transcriptId,
            String taskType,
            UUID correlationId
    ) {
        return store.values().stream()
                .filter(job -> job.tenantId().equals(tenantId))
                .filter(job -> job.meetingOccurrenceId().equals(meetingOccurrenceId))
                .filter(job -> job.transcriptId().equals(transcriptId))
                .filter(job -> job.taskType().equals(taskType))
                .filter(job -> job.correlationId().equals(correlationId))
                .filter(job -> job.status().isActive())
                .findFirst();
    }

    @Override
    public List<AiJob> findByStatus(AiJobStatus status) {
        return store.values().stream()
                .filter(job -> job.status() == status)
                .toList();
    }

    @Override
    public int countByTenantAndStatus(UUID tenantId, AiJobStatus status) {
        return (int) store.values().stream()
                .filter(job -> job.tenantId().equals(tenantId))
                .filter(job -> job.status() == status)
                .count();
    }

    @Override
    public List<AiJob> findQueuedOrdered() {
        List<AiJob> queued = new ArrayList<>(findByStatus(AiJobStatus.QUEUED));
        queued.sort(Comparator.comparing(AiJob::queuedAt));
        return queued;
    }

    public void clear() {
        store.clear();
    }
}
