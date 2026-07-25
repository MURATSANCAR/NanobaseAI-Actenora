package com.nanobaseai.actenora.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.application.AiJobService;
import com.nanobaseai.actenora.aiprocessing.application.admission.DefaultAdmissionController;
import com.nanobaseai.actenora.aiprocessing.application.port.AdmissionController;
import com.nanobaseai.actenora.aiprocessing.application.port.JobScheduler;
import com.nanobaseai.actenora.aiprocessing.application.port.ModelRouter;
import com.nanobaseai.actenora.aiprocessing.application.port.RoutableCandidate;
import com.nanobaseai.actenora.aiprocessing.application.routing.CapabilityModelRouter;
import com.nanobaseai.actenora.aiprocessing.application.scheduling.FairJobScheduler;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobException;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobStatus;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryAiAttemptRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryAiJobRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryModelCatalog;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryTenantAiPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiJobAdmissionRoutingSchedulerTest {

    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private Instant now;

    private InMemoryAiJobRepository jobs;
    private InMemoryAiAttemptRepository attempts;
    private InMemoryModelCatalog catalog;
    private InMemoryTenantAiPolicy policy;
    private ModelRouter router;
    private FairJobScheduler scheduler;
    private AiJobService service;

    private UUID modelBestId;
    private UUID modelAltId;
    private UUID deploymentBestId;
    private UUID deploymentAltId;
    private UUID deploymentUnhealthyId;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-07-25T12:00:00Z");
        jobs = new InMemoryAiJobRepository();
        attempts = new InMemoryAiAttemptRepository();
        catalog = new InMemoryModelCatalog();
        policy = new InMemoryTenantAiPolicy();
        policy.allow(tenantA, "local-best", "local-alt");
        policy.allow(tenantB, "local-best", "local-alt");
        policy.setMaxConcurrentAiJobs(tenantA, 1);
        policy.setMaxConcurrentAiJobs(tenantB, 1);
        policy.setCriticalFallbackAllowed(tenantA, false);

        modelBestId = UUID.randomUUID();
        modelAltId = UUID.randomUUID();
        deploymentBestId = UUID.randomUUID();
        deploymentAltId = UUID.randomUUID();
        deploymentUnhealthyId = UUID.randomUUID();

        catalog.add(candidate(
                modelBestId, "local-best", deploymentBestId, "best-1",
                true, true, true, 8192, 0, 2, 0, 0, 0.95, 0.7, 10
        ));
        catalog.add(candidate(
                modelAltId, "local-alt", deploymentAltId, "alt-1",
                true, true, true, 8192, 0, 2, 0, 0, 0.5, 0.9, 20
        ));

        router = new CapabilityModelRouter(catalog, policy);
        scheduler = new FairJobScheduler(
                jobs, attempts, policy, router,
                Duration.ofMinutes(1), 50, Duration.ofSeconds(10)
        );
        DefaultAdmissionController admission = new DefaultAdmissionController(jobs, policy, router, scheduler);
        service = new AiJobService(admission, jobs, attempts, scheduler);
    }

    @Test
    void healthyBestModelIsSelected() {
        AdmissionController.AdmissionDecision decision = service.submit(command(tenantA, JobPriority.NORMAL, 1000));
        assertTrue(decision.admitted());
        assertEquals(modelBestId, decision.job().selectedModelId().orElseThrow());
        assertTrue(decision.job().selectedRoute().orElseThrow().reason().contains("healthy_best_model"));
    }

    @Test
    void unhealthyModelIsSkipped() {
        catalog.replaceAll(java.util.List.of(
                candidate(modelBestId, "local-best", deploymentUnhealthyId, "best-unhealthy",
                        false, true, false, 8192, 0, 2, 0, 0, 0.95, 0.7, 10),
                candidate(modelAltId, "local-alt", deploymentAltId, "alt-1",
                        true, true, true, 8192, 0, 2, 0, 0, 0.5, 0.9, 20)
        ));
        AdmissionController.AdmissionDecision decision = service.submit(command(tenantA, JobPriority.NORMAL, 1000));
        assertTrue(decision.admitted());
        assertEquals(modelAltId, decision.job().selectedModelId().orElseThrow());
        assertTrue(decision.job().selectedRoute().orElseThrow().rejectReasons().stream()
                .anyMatch(r -> r.contains("unhealthy")));
    }

    @Test
    void contextTooLargeIsRejected() {
        AdmissionController.AdmissionDecision decision = service.submit(command(tenantA, JobPriority.NORMAL, 100_000));
        assertFalse(decision.admitted());
        assertTrue(decision.rejectReason().contains("CONTEXT_TOO_LARGE"));
    }

    @Test
    void tenantDisallowedModelIsRejected() {
        policy.replaceAllowlist(tenantA, "other-model-only");
        AdmissionController.AdmissionDecision decision = service.submit(command(tenantA, JobPriority.NORMAL, 1000));
        assertFalse(decision.admitted());
        assertTrue(decision.rejectReason().contains("TENANT_DISALLOWED_MODEL"));
    }

    @Test
    void criticalFallbackForbidden() {
        catalog.replaceAll(java.util.List.of(
                candidate(modelBestId, "local-best", deploymentUnhealthyId, "best-unhealthy",
                        false, true, false, 8192, 0, 2, 0, 0, 0.95, 0.7, 10),
                candidate(modelAltId, "local-alt", deploymentAltId, "alt-1",
                        true, true, true, 8192, 0, 2, 0, 0, 0.5, 0.9, 20)
        ));
        ModelRouter.RouteResult result = router.route(new ModelRouter.RouteRequest(
                tenantA,
                "summarize",
                AiCapability.SUMMARIZATION,
                "tr",
                1000,
                JobPriority.CRITICAL,
                false,
                Optional.of(modelBestId),
                now
        ));
        assertFalse(result.routed());
        assertEquals("CRITICAL_FALLBACK_FORBIDDEN", result.failureCode());
    }

    @Test
    void capacityExhausted() {
        catalog.replaceAll(java.util.List.of(
                candidate(modelBestId, "local-best", deploymentBestId, "best-1",
                        true, true, true, 8192, 0, 1, 1, 0, 0.95, 0.7, 10)
        ));
        AdmissionController.AdmissionDecision decision = service.submit(command(tenantA, JobPriority.NORMAL, 1000));
        assertFalse(decision.admitted());
        assertTrue(decision.rejectReason().contains("CAPACITY_EXHAUSTED"));
    }

    @Test
    void starvationPreventionViaFairScheduling() {
        // Seed fairness cursor: tenant A gets served once via the scheduler.
        assertTrue(service.submit(commandWithCorrelation(tenantA, JobPriority.NORMAL, 1000, "corr-a-run")).admitted());
        Optional<JobScheduler.ClaimedJob> first = service.claimNext(now);
        assertTrue(first.isPresent());
        assertEquals(tenantA, first.get().job().tenantId());
        first.get().job().markSucceeded(10, 10, now.plusSeconds(1));
        jobs.save(first.get().job());

        AiJob queuedA = service.submit(commandWithCorrelation(tenantA, JobPriority.NORMAL, 1000, "corr-a-q")).job();
        AiJob queuedB = service.submit(commandWithCorrelation(tenantB, JobPriority.NORMAL, 1000, "corr-b-q")).job();
        assertEquals(AiJobStatus.QUEUED, queuedA.status());
        assertEquals(AiJobStatus.QUEUED, queuedB.status());

        // Equal priority: fairness prefers tenant B (served less).
        Optional<JobScheduler.ClaimedJob> claimed = service.claimNext(now.plusSeconds(2));
        assertTrue(claimed.isPresent());
        assertEquals(tenantB, claimed.get().job().tenantId());
    }

    @Test
    void duplicateJobIsRejected() {
        AdmissionController.SubmitAiJobCommand cmd = commandWithCorrelation(tenantA, JobPriority.NORMAL, 1000, "dup-1");
        assertTrue(service.submit(cmd).admitted());
        AiJobException ex = assertThrows(AiJobException.class, () -> service.submit(cmd));
        assertEquals("AI_JOB_DUPLICATE", ex.code());
    }

    @Test
    void cancellationWorksForQueuedAndRunning() {
        AiJob queued = service.submit(commandWithCorrelation(tenantA, JobPriority.NORMAL, 1000, "cancel-q")).job();
        service.cancel(queued.id(), now);
        assertEquals(AiJobStatus.CANCELLED, jobs.findById(queued.id()).orElseThrow().status());

        AiJob running = admitAndForceRunning(tenantA, "cancel-r");
        service.cancel(running.id(), now.plusSeconds(1));
        assertEquals(AiJobStatus.CANCELLED, jobs.findById(running.id()).orElseThrow().status());
        assertTrue(attempts.findByJobId(running.id()).stream()
                .anyMatch(a -> a.status().name().equals("CANCELLED")));
    }

    @Test
    void adminOverrideOnlyForAdmin() {
        AiJob job = service.submit(command(tenantA, JobPriority.NORMAL, 1000)).job();
        assertThrows(AiJobException.class, () ->
                service.adminOverrideRoute(job.id(), modelAltId, deploymentAltId, "local-alt", false, now));
        AiJob overridden = service.adminOverrideRoute(
                job.id(), modelAltId, deploymentAltId, "local-alt", true, now);
        assertEquals(modelAltId, overridden.selectedModelId().orElseThrow());
        assertEquals("admin_manual_override", overridden.selectedRoute().orElseThrow().reason());
    }

    @Test
    void staleRunningJobRecovery() {
        AiJob running = admitAndForceRunning(tenantA, "stale-1");
        int recovered = service.recoverStale(now.plus(Duration.ofMinutes(30)), Duration.ofMinutes(5));
        assertEquals(1, recovered);
        assertEquals(AiJobStatus.QUEUED, jobs.findById(running.id()).orElseThrow().status());
    }

    @Test
    void priorityAgingPreventsStarvationOfLowPriority() {
        AiJob bulk = service.submit(commandWithCorrelation(tenantA, JobPriority.BULK, 1000, "bulk-1")).job();
        // Simulate age by using older queuedAt through re-save isn't possible; use claim score directly.
        Instant later = now.plus(Duration.ofMinutes(20));
        long bulkScore = bulk.schedulingScore(later, Duration.ofMinutes(1), 50);
        AiJob freshNormal = AiJob.enqueue(
                tenantA, UUID.randomUUID(), UUID.randomUUID(), "summarize",
                JobPriority.NORMAL, AiCapability.SUMMARIZATION, "p1", "s1", "tr", 1000, true,
                later, later.plus(Duration.ofHours(1)), UUID.randomUUID());
        long normalScore = freshNormal.schedulingScore(later, Duration.ofMinutes(1), 50);
        assertTrue(bulkScore > normalScore, "aged BULK should outrank fresh NORMAL");
    }

    @Test
    void selectedRouteReasonIsStored() {
        AiJob job = service.submit(command(tenantA, JobPriority.HIGH, 500)).job();
        assertNotNull(job.selectedRoute().orElseThrow().reason());
        assertTrue(job.selectedRoute().orElseThrow().reason().contains("SUMMARIZATION"));
    }

    private AiJob admitAndForceRunning(UUID tenantId, String correlation) {
        AiJob job = service.submit(commandWithCorrelation(tenantId, JobPriority.NORMAL, 1000, correlation)).job();
        var attempt = job.markRunning(now);
        jobs.save(job);
        attempts.save(attempt);
        return job;
    }

    private AdmissionController.SubmitAiJobCommand command(UUID tenantId, JobPriority priority, int contextSize) {
        return commandWithCorrelation(tenantId, priority, contextSize, UUID.randomUUID().toString());
    }

    private AdmissionController.SubmitAiJobCommand commandWithCorrelation(
            UUID tenantId,
            JobPriority priority,
            int contextSize,
            String correlationKey
    ) {
        UUID correlation = UUID.nameUUIDFromBytes((tenantId + correlationKey).getBytes());
        return new AdmissionController.SubmitAiJobCommand(
                tenantId,
                UUID.nameUUIDFromBytes(("meeting-" + correlationKey).getBytes()),
                UUID.nameUUIDFromBytes(("transcript-" + correlationKey).getBytes()),
                "summarize",
                priority,
                AiCapability.SUMMARIZATION,
                "prompt-v1",
                "schema-v1",
                "tr",
                contextSize,
                null,
                correlation,
                now
        );
    }

    private static RoutableCandidate candidate(
            UUID modelId,
            String modelKey,
            UUID deploymentId,
            String deploymentKey,
            boolean healthy,
            boolean modelAccepts,
            boolean deploymentAccepts,
            int contextWindow,
            int minContext,
            int maxConcurrency,
            int currentConcurrency,
            int queueDepth,
            double quality,
            double speed,
            int priority
    ) {
        return new RoutableCandidate(
                modelId,
                modelKey,
                deploymentId,
                deploymentKey,
                Set.of(AiCapability.SUMMARIZATION),
                contextWindow,
                minContext,
                Set.of("tr", "en"),
                healthy,
                modelAccepts,
                deploymentAccepts,
                maxConcurrency,
                currentConcurrency,
                queueDepth,
                quality,
                speed,
                priority
        );
    }
}
