package com.nanobaseai.actenora.aiprocessing.application;

import com.nanobaseai.actenora.aiprocessing.application.port.AdmissionController;
import com.nanobaseai.actenora.aiprocessing.application.port.AiAttemptRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.AiJobDeadNotifier;
import com.nanobaseai.actenora.aiprocessing.application.port.AiJobRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.JobScheduler;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiAttempt;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobException;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobStatus;
import com.nanobaseai.actenora.sharedkernel.coordination.JobProgressCache;
import com.nanobaseai.actenora.sharedkernel.coordination.NoOpJobProgressCache;

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
    private final JobProgressCache progressCache;
    private final AiJobDeadNotifier deadNotifier;

    public AiJobService(
            AdmissionController admissionController,
            AiJobRepository jobs,
            AiAttemptRepository attempts,
            JobScheduler scheduler
    ) {
        this(admissionController, jobs, attempts, scheduler, new NoOpJobProgressCache(), AiJobDeadNotifier.noop());
    }

    public AiJobService(
            AdmissionController admissionController,
            AiJobRepository jobs,
            AiAttemptRepository attempts,
            JobScheduler scheduler,
            JobProgressCache progressCache
    ) {
        this(admissionController, jobs, attempts, scheduler, progressCache, AiJobDeadNotifier.noop());
    }

    public AiJobService(
            AdmissionController admissionController,
            AiJobRepository jobs,
            AiAttemptRepository attempts,
            JobScheduler scheduler,
            JobProgressCache progressCache,
            AiJobDeadNotifier deadNotifier
    ) {
        this.admissionController = Objects.requireNonNull(admissionController, "admissionController");
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.attempts = Objects.requireNonNull(attempts, "attempts");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.progressCache = Objects.requireNonNull(progressCache, "progressCache");
        this.deadNotifier = Objects.requireNonNull(deadNotifier, "deadNotifier");
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
        publishProgress(job, "cancelled", now);
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
        Optional<JobScheduler.ClaimedJob> claimed = scheduler.claimNext(now);
        claimed.ifPresent(c -> publishProgress(c.job(), "running", now));
        return claimed;
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
        publishProgress(job, "succeeded", now);
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
        publishProgress(job, retryable ? "retry" : "dead", now);
        if (job.status() == AiJobStatus.DEAD) {
            try {
                deadNotifier.onPermanentlyFailed(job);
            } catch (RuntimeException ignored) {
                // Notification must not fail the durable job path.
            }
        }
        return job;
    }

    public int recoverStale(Instant now, java.time.Duration staleAfter) {
        return scheduler.recoverStaleRunning(now, staleAfter);
    }

    public int recoverStale(Instant now, java.time.Duration staleAfter, int maxAttempts) {
        return scheduler.recoverStaleRunning(now, staleAfter, maxAttempts);
    }

    public Optional<AiJob> find(UUID jobId) {
        return jobs.findById(jobId);
    }

    public List<AiJob> listForTenant(UUID tenantId) {
        return jobs.listByTenant(tenantId);
    }

    private void publishProgress(AiJob job, String stage, Instant now) {
        try {
            progressCache.put(
                    job.meetingOccurrenceId(),
                    new JobProgressCache.Progress(
                            job.id(),
                            job.status().name(),
                            stage,
                            job.attemptCount(),
                            now
                    )
            );
        } catch (RuntimeException ignored) {
            // Progress cache must never fail the durable job path.
        }
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
