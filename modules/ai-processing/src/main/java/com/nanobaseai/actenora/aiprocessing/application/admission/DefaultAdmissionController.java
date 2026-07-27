package com.nanobaseai.actenora.aiprocessing.application.admission;

import com.nanobaseai.actenora.aiprocessing.application.port.AdmissionController;
import com.nanobaseai.actenora.aiprocessing.application.port.AiJobRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.JobScheduler;
import com.nanobaseai.actenora.aiprocessing.application.port.ModelRouter;
import com.nanobaseai.actenora.aiprocessing.application.port.TenantAiPolicyPort;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobException;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobStatus;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Admits AI jobs when capacity, uniqueness, and initial routing succeed.
 */
public final class DefaultAdmissionController implements AdmissionController {

    private final AiJobRepository jobs;
    private final TenantAiPolicyPort tenantPolicy;
    private final ModelRouter modelRouter;
    private final JobScheduler scheduler;

    public DefaultAdmissionController(
            AiJobRepository jobs,
            TenantAiPolicyPort tenantPolicy,
            ModelRouter modelRouter,
            JobScheduler scheduler
    ) {
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.tenantPolicy = Objects.requireNonNull(tenantPolicy, "tenantPolicy");
        this.modelRouter = Objects.requireNonNull(modelRouter, "modelRouter");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public AdmissionDecision admit(SubmitAiJobCommand command) {
        Objects.requireNonNull(command, "command");

        Optional<AiJob> duplicate = jobs.findDuplicate(
                command.tenantId(),
                command.meetingOccurrenceId(),
                command.transcriptId(),
                command.taskType(),
                command.correlationId()
        );
        if (duplicate.isPresent()) {
            throw AiJobException.duplicate(
                    "Active job already exists for correlation " + command.correlationId()
            );
        }

        // Transcript+taskType idempotency for extraction-class jobs (legacy path).
        if (isExtractionTask(command.taskType())) {
            Optional<AiJob> latest = jobs.findLatestByTranscriptAndTaskType(
                    command.tenantId(), command.transcriptId(), command.taskType());
            if (latest.isPresent()) {
                AiJob prior = latest.get();
                if (prior.status().isActive()) {
                    return AdmissionDecision.rejected(
                            "already_active: job " + prior.id() + " status=" + prior.status());
                }
                if (prior.status() == AiJobStatus.SUCCEEDED && !command.forceReprocess()) {
                    return AdmissionDecision.rejected(
                            "already_extracted: job " + prior.id() + " succeeded; pass forceReprocess=true to rerun");
                }
            }
        }

        int running = jobs.countByTenantAndStatus(command.tenantId(), AiJobStatus.RUNNING);
        int queued = jobs.countByTenantAndStatus(command.tenantId(), AiJobStatus.QUEUED);
        int max = tenantPolicy.maxConcurrentAiJobs(command.tenantId());
        // Soft admit into queue; hard reject only when queue already at 4x concurrency ceiling.
        if (running + queued >= max * 4) {
            return AdmissionDecision.rejected("capacity_exhausted: tenant queue saturated");
        }

        boolean fallbackPermitted = resolveFallback(command);
        Duration sla = tenantPolicy.slaTarget(command.tenantId(), command.priority());
        Duration estimatedWait = scheduler.estimateQueueWait(
                command.tenantId(), command.priority(), command.now());
        if (estimatedWait.compareTo(sla.multipliedBy(2)) > 0 && command.priority().isCritical()) {
            return AdmissionDecision.rejected("admission_rejected: estimated wait exceeds critical SLA");
        }

        ModelRouter.RouteResult routeProbe = modelRouter.route(new ModelRouter.RouteRequest(
                command.tenantId(),
                command.taskType(),
                command.requestedCapability(),
                command.language(),
                command.contextSize(),
                command.priority(),
                fallbackPermitted,
                Optional.empty(),
                command.now()
        ));
        if (!routeProbe.routed()) {
            return AdmissionDecision.rejected(
                    routeProbe.failureCode() + ": " + String.join("; ", routeProbe.rejectReasons())
            );
        }

        AiJob job = AiJob.enqueue(
                command.tenantId(),
                command.meetingOccurrenceId(),
                command.transcriptId(),
                command.taskType(),
                command.priority(),
                command.requestedCapability(),
                command.promptVersion(),
                command.schemaVersion(),
                command.language(),
                command.contextSize(),
                fallbackPermitted,
                command.now(),
                command.now().plus(sla),
                command.correlationId()
        );
        job.applyRoute(routeProbe.selected());
        jobs.save(job);
        return AdmissionDecision.accepted(job, estimatedWait);
    }

    private boolean resolveFallback(SubmitAiJobCommand command) {
        boolean policyAllows = tenantPolicy.isCriticalFallbackAllowed(command.tenantId());
        if (command.fallbackPermittedOverride() != null) {
            if (command.priority().isCritical() && Boolean.TRUE.equals(command.fallbackPermittedOverride())) {
                return policyAllows;
            }
            return command.fallbackPermittedOverride();
        }
        if (command.priority().isCritical()) {
            return policyAllows;
        }
        return true;
    }

    private static boolean isExtractionTask(String taskType) {
        if (taskType == null) {
            return false;
        }
        return "CHUNK_EXTRACTION".equals(taskType)
                || "PIPELINE_ROOT".equals(taskType)
                || "NORMALIZE".equals(taskType);
    }
}
