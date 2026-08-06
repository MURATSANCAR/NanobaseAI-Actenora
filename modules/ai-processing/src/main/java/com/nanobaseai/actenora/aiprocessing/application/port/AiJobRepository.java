package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobStatus;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingStage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiJobRepository {

    void save(AiJob job);

    Optional<AiJob> findById(UUID id);

    Optional<AiJob> findByIdempotencyKey(UUID tenantId, String idempotencyKey);

    Optional<AiJob> findDuplicate(
            UUID tenantId,
            UUID meetingOccurrenceId,
            UUID transcriptId,
            String taskType,
            UUID correlationId
    );

    /**
     * Latest job for transcript+taskType (any status), ordered by queued_at DESC.
     */
    Optional<AiJob> findLatestByTranscriptAndTaskType(
            UUID tenantId,
            UUID transcriptId,
            String taskType
    );

    /** Active extraction work for the meeting, regardless of transcript revision or stage. */
    Optional<AiJob> findActiveByMeetingAndCapability(
            UUID tenantId,
            UUID meetingOccurrenceId,
            AiCapability capability
    );

    List<AiJob> findByStatus(AiJobStatus status);

    List<AiJob> findByParentJobId(UUID parentJobId);

    int countByTenantAndStatus(UUID tenantId, AiJobStatus status);

    List<AiJob> findQueuedOrdered();

    /**
     * Atomically locks up to {@code limit} eligible QUEUED jobs (deps satisfied, eligible at now)
     * using {@code FOR UPDATE SKIP LOCKED}. Caller must complete claim (route + markRunning) and save.
     */
    List<AiJob> lockEligibleQueued(Instant now, int limit);

    /**
     * Same as {@link #lockEligibleQueued(Instant, int)} filtered by pipeline stage.
     */
    List<AiJob> lockEligibleQueuedByStage(Instant now, ProcessingStage stage, int limit);

    List<AiJob> listByTenant(UUID tenantId);
}
