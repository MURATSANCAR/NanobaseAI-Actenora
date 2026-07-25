package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.domain.job.AiAttempt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiAttemptRepository {

    void save(AiAttempt attempt);

    Optional<AiAttempt> findById(UUID id);

    List<AiAttempt> findByJobId(UUID jobId);

    Optional<AiAttempt> findActiveByJobId(UUID jobId);
}
