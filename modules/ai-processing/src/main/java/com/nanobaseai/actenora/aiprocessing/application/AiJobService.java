package com.nanobaseai.actenora.aiprocessing.application;

import com.nanobaseai.actenora.aiprocessing.application.port.AdmissionController;
import com.nanobaseai.actenora.aiprocessing.application.port.AiAttemptRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.AiJobRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.JobScheduler;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobException;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application façade for AI job submit, cancel, and admin override.
 */
public final class AiJobService {

    private final AdmissionController admissionController;
    private final AiJobRepository jobs;
    private final AiAttemptRepository attempts;
    private final JobScheduler scheduler;

    public AiJobService(
            AdmissionController admissionController,
            AiJobRepository jobs,
            AiAttemptRepository attempts,
            JobScheduler scheduler
    ) {
        this.admissionController = Objects.requireNonNull(admissionController, "admissionController");
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.attempts = Objects.requireNonNull(attempts, "attempts");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public AdmissionController.AdmissionDecision submit(AdmissionController.SubmitAiJobCommand command) {
        return admissionController.admit(command);
    }

    public AiJob cancel(UUID jobId, Instant now) {
        AiJob job = requireJob(jobId);
        if (job.status() == AiJobStatus.RUNNING) {
            attempts.findActiveByJobId(jobId).ifPresent(attempt -> {
                attempt.cancel(now);
                attempts.save(attempt);
            });
        }
        job.cancel(now);
        jobs.save(job);
        return job;
    }

    public AiJob adminOverrideRoute(
            UUID jobId,
            UUID modelDefinitionId,
            UUID deploymentId,
            String modelKey,
            boolean actorIsAdmin,
            Instant now
    ) {
        if (!actorIsAdmin) {
            throw AiJobException.forbidden("Manual route override requires admin");
        }
        AiJob job = requireJob(jobId);
        job.applyAdminOverride(modelDefinitionId, deploymentId, modelKey, now);
        jobs.save(job);
        return job;
    }

    public Optional<JobScheduler.ClaimedJob> claimNext(Instant now) {
        return scheduler.claimNext(now);
    }

    public int recoverStale(Instant now, java.time.Duration staleAfter) {
        return scheduler.recoverStaleRunning(now, staleAfter);
    }

    public Optional<AiJob> find(UUID jobId) {
        return jobs.findById(jobId);
    }

    private AiJob requireJob(UUID jobId) {
        return jobs.findById(jobId)
                .orElseThrow(() -> AiJobException.notFound("Job not found: " + jobId));
    }
}
