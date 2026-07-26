package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.domain.job.AiAttempt;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiJobRepository {

    void save(AiJob job);

    Optional<AiJob> findById(UUID id);

    Optional<AiJob> findDuplicate(
            UUID tenantId,
            UUID meetingOccurrenceId,
            UUID transcriptId,
            String taskType,
            UUID correlationId
    );

    List<AiJob> findByStatus(AiJobStatus status);

    int countByTenantAndStatus(UUID tenantId, AiJobStatus status);

    List<AiJob> findQueuedOrdered();

    List<AiJob> listByTenant(UUID tenantId);
}
