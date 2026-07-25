package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.domain.job.AiAttempt;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Fair job scheduler with priority aging and tenant concurrency limits.
 */
public interface JobScheduler {

    Optional<ClaimedJob> claimNext(Instant now);

    Duration estimateQueueWait(UUID tenantId, JobPriority priority, Instant now);

    int recoverStaleRunning(Instant now, Duration staleAfter);

    record ClaimedJob(AiJob job, AiAttempt attempt) {
    }
}
