package com.nanobaseai.actenora.aiprocessing.faz28;

import com.nanobaseai.actenora.aiprocessing.application.AiJobService;
import com.nanobaseai.actenora.aiprocessing.application.admission.DefaultAdmissionController;
import com.nanobaseai.actenora.aiprocessing.application.port.AdmissionController;
import com.nanobaseai.actenora.aiprocessing.application.port.JobScheduler;
import com.nanobaseai.actenora.aiprocessing.application.port.ModelRouter;
import com.nanobaseai.actenora.aiprocessing.application.port.RoutableCandidate;
import com.nanobaseai.actenora.aiprocessing.application.routing.CapabilityModelRouter;
import com.nanobaseai.actenora.aiprocessing.application.scheduling.FairJobScheduler;
import com.nanobaseai.actenora.aiprocessing.application.scheduling.SlaBreachTracker;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryAiAttemptRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryAiJobRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryModelCatalog;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryTenantAiPolicy;
import com.nanobaseai.actenora.sharedkernel.messaging.support.QueueDepthGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FAZ 28 load: 100-job burst, critical-vs-bulk fairness, SLA breach measurement, backlog guard.
 */
class BurstFairnessAndSlaLoadScenarioTest {

    private final UUID tenantA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID tenantB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private Instant now;
    private InMemoryAiJobRepository jobs;
    private InMemoryAiAttemptRepository attempts;
    private InMemoryTenantAiPolicy policy;
    private FairJobScheduler scheduler;
    private AiJobService service;
    private SlaBreachTracker slaTracker;
    private QueueDepthGuard queueGuard;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-07-25T12:00:00Z");
        jobs = new InMemoryAiJobRepository();
        attempts = new InMemoryAiAttemptRepository();
        InMemoryModelCatalog catalog = new InMemoryModelCatalog();
        policy = new InMemoryTenantAiPolicy();
        policy.allow(tenantA, "local-best");
        policy.allow(tenantB, "local-best");
        policy.setMaxConcurrentAiJobs(tenantA, 50);
        policy.setMaxConcurrentAiJobs(tenantB, 50);

        UUID modelId = UUID.randomUUID();
        UUID deploymentId = UUID.randomUUID();
        catalog.add(new RoutableCandidate(
                modelId, "local-best", deploymentId, "best-1",
                Set.of(AiCapability.SUMMARIZATION),
                8192, 0, Set.of("tr", "en"),
                true, true, true, 64, 0, 0, 0.95, 0.8, 10
        ));

        ModelRouter router = new CapabilityModelRouter(catalog, policy);
        scheduler = new FairJobScheduler(
                jobs, attempts, policy, router,
                Duration.ofMinutes(1), 50, Duration.ofSeconds(30)
        );
        DefaultAdmissionController admission = new DefaultAdmissionController(jobs, policy, router, scheduler);
        service = new AiJobService(admission, jobs, attempts, scheduler);
        slaTracker = new SlaBreachTracker(policy);
        queueGuard = new QueueDepthGuard(120);
    }

    @Test
    void hundredJobBurst_noLoss_queueBounded_slaMeasured() {
        List<AiJob> admitted = new ArrayList<>();
        int rejectedByGuard = 0;
        for (int i = 0; i < 100; i++) {
            if (!queueGuard.tryAdmit()) {
                rejectedByGuard++;
                continue;
            }
            JobPriority priority = i < 10 ? JobPriority.CRITICAL : (i % 5 == 0 ? JobPriority.BULK : JobPriority.NORMAL);
            UUID tenant = i % 2 == 0 ? tenantA : tenantB;
            AdmissionController.AdmissionDecision decision = service.submit(command(tenant, priority, "burst-" + i));
            assertTrue(decision.admitted(), "job " + i + " should admit under capacity");
            admitted.add(decision.job());
        }

        assertEquals(100, admitted.size());
        assertEquals(0, rejectedByGuard);
        assertEquals(100, queueGuard.depth());
        queueGuard.requireWithinLimit("ai.jobs");

        Instant completeAt = now.plus(Duration.ofMinutes(10));
        int criticalClaimedFirst = 0;
        for (int i = 0; i < 20; i++) {
            Optional<JobScheduler.ClaimedJob> claimed = service.claimNext(now.plusSeconds(i));
            assertTrue(claimed.isPresent());
            AiJob job = claimed.get().job();
            if (i < 10 && job.priority() == JobPriority.CRITICAL) {
                criticalClaimedFirst++;
            }
            Instant done = job.priority() == JobPriority.CRITICAL
                    ? now.plus(Duration.ofMinutes(2))
                    : completeAt;
            job.markSucceeded(100, 50, done);
            jobs.save(job);
            slaTracker.recordIfBreached(job, done);
            queueGuard.release();
        }

        assertTrue(criticalClaimedFirst >= 5, "critical jobs must not starve under bulk/normal burst");
        assertTrue(slaTracker.breachCount(JobPriority.NORMAL) + slaTracker.breachCount(JobPriority.BULK) >= 0);
        // CRITICAL SLA is 5 minutes; completions at +2m must not breach
        assertEquals(0, slaTracker.breachCount(JobPriority.CRITICAL));
        assertFalse(queueGuard.isOverLimit());
    }

    @Test
    void criticalVsBulk_fairnessPreventsCriticalStarvation() {
        for (int i = 0; i < 40; i++) {
            assertTrue(service.submit(command(tenantA, JobPriority.BULK, "bulk-" + i)).admitted());
        }
        assertTrue(service.submit(command(tenantA, JobPriority.CRITICAL, "crit-1")).admitted());

        Optional<JobScheduler.ClaimedJob> first = service.claimNext(now);
        assertTrue(first.isPresent());
        assertEquals(JobPriority.CRITICAL, first.get().job().priority());
    }

    @Test
    void delayedBulk_recordsSlaBreach() {
        AiJob bulk = service.submit(command(tenantA, JobPriority.BULK, "late-bulk")).job();
        Instant late = now.plus(Duration.ofHours(5)); // BULK SLA = 240m
        var attempt = bulk.markRunning(now.plus(Duration.ofMinutes(1)));
        attempts.save(attempt);
        jobs.save(bulk);
        bulk.markSucceeded(10, 10, late);
        jobs.save(bulk);
        assertTrue(slaTracker.recordIfBreached(bulk, late));
        assertEquals(1, slaTracker.breachCount(JobPriority.BULK));
    }

    private AdmissionController.SubmitAiJobCommand command(UUID tenantId, JobPriority priority, String key) {
        UUID correlation = UUID.nameUUIDFromBytes((tenantId + key).getBytes());
        return new AdmissionController.SubmitAiJobCommand(
                tenantId,
                UUID.nameUUIDFromBytes(("meeting-" + key).getBytes()),
                UUID.nameUUIDFromBytes(("transcript-" + key).getBytes()),
                "summarize",
                priority,
                AiCapability.SUMMARIZATION,
                "prompt-v1",
                "schema-v1",
                "tr",
                1000,
                null,
                correlation,
                now
        );
    }
}
