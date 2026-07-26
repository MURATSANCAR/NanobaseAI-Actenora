package com.nanobaseai.actenora.aiprocessing.application;

import com.nanobaseai.actenora.aiprocessing.application.port.AdmissionController;
import com.nanobaseai.actenora.aiprocessing.application.port.AiAttemptRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.AiJobRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.JobScheduler;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiAttempt;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobException;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobStatus;

import java.time.Instant;
import java.util.List;
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

    /**
     * Terminates the active attempt and the job after a successful inference call.
     */
    public AiJob completeAttempt(UUID jobId, long latencyMs, int inputTokens, int outputTokens, Instant now) {
        AiJob job = requireJob(jobId);
        AiAttempt attempt = requireActiveAttempt(jobId);
        attempt.completeSuccess(latencyMs, inputTokens, outputTokens, now);
        attempts.save(attempt);
        job.markSucceeded(inputTokens, outputTokens, now);
        jobs.save(job);
        return job;
    }

    /**
     * Fails the active attempt. Retryable failures return the job to the queue,
     * permanent ones move it to DEAD.
     */
    public AiJob failAttempt(
            UUID jobId,
            long latencyMs,
            boolean retryable,
            String failureCategory,
            String failureDetailSafe,
            Instant now
    ) {
        AiJob job = requireJob(jobId);
        AiAttempt attempt = requireActiveAttempt(jobId);
        attempt.completeFailure(latencyMs, retryable, failureCategory, failureDetailSafe, now);
        attempts.save(attempt);
        job.markFailed(retryable, now);
        jobs.save(job);
        return job;
    }

    public int recoverStale(Instant now, java.time.Duration staleAfter) {
        return scheduler.recoverStaleRunning(now, staleAfter);
    }

    public Optional<AiJob> find(UUID jobId) {
        return jobs.findById(jobId);
    }

    public List<AiJob> listForTenant(UUID tenantId) {
        return jobs.listByTenant(tenantId);
    }

    private AiJob requireJob(UUID jobId) {
        return jobs.findById(jobId)
                .orElseThrow(() -> AiJobException.notFound("Job not found: " + jobId));
    }

    private AiAttempt requireActiveAttempt(UUID jobId) {
        return attempts.findActiveByJobId(jobId)
                .orElseThrow(() -> AiJobException.invalidTransition("No active attempt for job " + jobId));
    }
}
