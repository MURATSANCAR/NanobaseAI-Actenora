package com.nanobaseai.actenora.aiprocessing.infrastructure.persistence;

import com.nanobaseai.actenora.aiprocessing.application.port.AiJobRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.ProcessingJobDependencyRepository;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobStatus;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingStage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryAiJobRepository implements AiJobRepository {

    private final Map<UUID, AiJob> store = new ConcurrentHashMap<>();
    private final ProcessingJobDependencyRepository dependencies;

    public InMemoryAiJobRepository() {
        this(null);
    }

    public InMemoryAiJobRepository(ProcessingJobDependencyRepository dependencies) {
        this.dependencies = dependencies;
    }

    @Override
    public void save(AiJob job) {
        store.put(job.id(), job);
    }

    @Override
    public Optional<AiJob> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<AiJob> findByIdempotencyKey(UUID tenantId, String idempotencyKey) {
        return store.values().stream()
                .filter(job -> job.tenantId().equals(tenantId))
                .filter(job -> job.idempotencyKey().equals(idempotencyKey))
                .findFirst();
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
    public Optional<AiJob> findLatestByTranscriptAndTaskType(
            UUID tenantId,
            UUID transcriptId,
            String taskType
    ) {
        return store.values().stream()
                .filter(job -> job.tenantId().equals(tenantId))
                .filter(job -> job.transcriptId().equals(transcriptId))
                .filter(job -> job.taskType().equals(taskType))
                .max(Comparator.comparing(AiJob::queuedAt));
    }

    @Override
    public Optional<AiJob> findActiveByMeetingAndCapability(
            UUID tenantId,
            UUID meetingOccurrenceId,
            AiCapability capability
    ) {
        return store.values().stream()
                .filter(job -> job.tenantId().equals(tenantId))
                .filter(job -> job.meetingOccurrenceId().equals(meetingOccurrenceId))
                .filter(job -> job.requestedCapability() == capability)
                .filter(job -> job.status().isActive())
                .filter(job -> job.parentJobId().isEmpty())
                .min(Comparator.comparing(AiJob::queuedAt));
    }

    @Override
    public List<AiJob> findByStatus(AiJobStatus status) {
        return store.values().stream()
                .filter(job -> job.status() == status)
                .toList();
    }

    @Override
    public List<AiJob> findByParentJobId(UUID parentJobId) {
        return store.values().stream()
                .filter(job -> job.parentJobId().map(parentJobId::equals).orElse(false))
                .sorted(Comparator.comparing(AiJob::queuedAt))
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

    @Override
    public List<AiJob> lockEligibleQueued(Instant now, int limit) {
        return lockEligible(now, null, limit);
    }

    @Override
    public List<AiJob> lockEligibleQueuedByStage(Instant now, ProcessingStage stage, int limit) {
        return lockEligible(now, stage, limit);
    }

    private List<AiJob> lockEligible(Instant now, ProcessingStage stage, int limit) {
        return findQueuedOrdered().stream()
                .filter(job -> job.isEligibleAt(now))
                .filter(job -> stage == null || job.stage() == stage)
                .filter(job -> dependencies == null || dependencies.countUnsatisfied(job.id()) == 0)
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public List<AiJob> listByTenant(UUID tenantId) {
        return store.values().stream()
                .filter(job -> job.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(AiJob::queuedAt).reversed())
                .toList();
    }

    public void clear() {
        store.clear();
    }
}
