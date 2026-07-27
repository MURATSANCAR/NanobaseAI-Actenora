package com.nanobaseai.actenora.aiprocessing.api;

import com.nanobaseai.actenora.aiprocessing.application.AiJobService;
import com.nanobaseai.actenora.aiprocessing.application.port.AdmissionController;
import com.nanobaseai.actenora.aiprocessing.application.port.JobScheduler;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Public façade for the AI Processing bounded context.
 * Cross-module callers use types in this package only.
 */
public interface AiProcessingApi {

    AdmissionController.AdmissionDecision submitJob(AdmissionController.SubmitAiJobCommand command);

    AiJob cancelJob(UUID jobId, Instant now);

    AiJob adminOverrideRoute(
            UUID jobId,
            UUID modelDefinitionId,
            UUID deploymentId,
            String modelKey,
            boolean actorIsAdmin,
            Instant now
    );

    Optional<JobScheduler.ClaimedJob> claimNext(Instant now);

    int recoverStaleRunning(Instant now, Duration staleAfter);

    default int recoverStaleRunning(Instant now, Duration staleAfter, int maxAttempts) {
        return recoverStaleRunning(now, staleAfter);
    }

    Optional<AiJob> findJob(UUID jobId);

    List<AiJob> listJobsForTenant(UUID tenantId);

    /**
     * Default API adapter over {@link AiJobService}.
     */
    final class Default implements AiProcessingApi {

        private final AiJobService service;

        public Default(AiJobService service) {
            this.service = Objects.requireNonNull(service, "service");
        }

        @Override
        public AdmissionController.AdmissionDecision submitJob(AdmissionController.SubmitAiJobCommand command) {
            return service.submit(command);
        }

        @Override
        public AiJob cancelJob(UUID jobId, Instant now) {
            return service.cancel(jobId, now);
        }

        @Override
        public AiJob adminOverrideRoute(
                UUID jobId,
                UUID modelDefinitionId,
                UUID deploymentId,
                String modelKey,
                boolean actorIsAdmin,
                Instant now
        ) {
            return service.adminOverrideRoute(jobId, modelDefinitionId, deploymentId, modelKey, actorIsAdmin, now);
        }

        @Override
        public Optional<JobScheduler.ClaimedJob> claimNext(Instant now) {
            return service.claimNext(now);
        }

        @Override
        public int recoverStaleRunning(Instant now, Duration staleAfter) {
            return service.recoverStale(now, staleAfter);
        }

        @Override
        public int recoverStaleRunning(Instant now, Duration staleAfter, int maxAttempts) {
            return service.recoverStale(now, staleAfter, maxAttempts);
        }

        @Override
        public Optional<AiJob> findJob(UUID jobId) {
            return service.find(jobId);
        }

        @Override
        public List<AiJob> listJobsForTenant(UUID tenantId) {
            return service.listForTenant(tenantId);
        }
    }
}
