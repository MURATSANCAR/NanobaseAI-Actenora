package com.nanobaseai.actenora.aiprocessing.application.pipeline.staged;

import com.nanobaseai.actenora.aiprocessing.application.port.JobScheduler;
import com.nanobaseai.actenora.aiprocessing.application.port.ModelRouter;
import com.nanobaseai.actenora.aiprocessing.application.port.TenantAiPolicyPort;
import com.nanobaseai.actenora.aiprocessing.application.scheduling.FairJobScheduler;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobStatus;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingStage;
import com.nanobaseai.actenora.aiprocessing.domain.job.SelectedRoute;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryAiAttemptRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryAiJobRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryProcessingJobDependencyRepository;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryOutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.support.TenantFairnessTracker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageBarrierClaimTest {

    @Test
    void mergeNotClaimableUntilExtractDepsSatisfied() {
        InMemoryProcessingJobDependencyRepository deps = new InMemoryProcessingJobDependencyRepository();
        InMemoryAiJobRepository jobs = new InMemoryAiJobRepository(deps);
        InMemoryAiAttemptRepository attempts = new InMemoryAiAttemptRepository();
        StageCommandPublisher commands = new StageCommandPublisher(new InMemoryOutboxStore(new TenantFairnessTracker()));
        PipelineGraphFactory factory = new PipelineGraphFactory(jobs, deps, commands);

        TenantAiPolicyPort policy = new TenantAiPolicyPort() {
            @Override
            public boolean isModelAllowed(UUID tenantId, String modelKey) {
                return true;
            }

            @Override
            public boolean isCriticalFallbackAllowed(UUID tenantId) {
                return true;
            }

            @Override
            public int maxConcurrentAiJobs(UUID tenantId) {
                return 8;
            }

            @Override
            public Duration slaTarget(UUID tenantId, JobPriority priority) {
                return Duration.ofHours(1);
            }

            @Override
            public Set<String> allowedModelKeys(UUID tenantId) {
                return Set.of("local");
            }
        };
        ModelRouter router = request -> ModelRouter.RouteResult.success(
                new SelectedRoute(
                        UUID.fromString("00000000-0000-4000-8000-000000000001"),
                        UUID.fromString("00000000-0000-4000-8000-000000000002"),
                        "local",
                        "test",
                        List.of(),
                        request.now()
                ),
                List.of()
        );
        FairJobScheduler scheduler = new FairJobScheduler(jobs, attempts, policy, router);

        UUID tenant = UUID.randomUUID();
        UUID meeting = UUID.randomUUID();
        UUID transcript = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-27T13:00:00Z");
        var admission = factory.admitFromTranscriptReady(
                tenant, meeting, transcript, "barrier", JobPriority.NORMAL, "tr", 5,
                UUID.randomUUID(), now, Duration.ofHours(1));
        var chunk = factory.expandAfterTriageFullPath(admission.triage(), "barrier", now).getFirst();
        deps.markSatisfiedForCompletedDependency(admission.triage().id());
        var expanded = factory.expandExtractGraph(chunk, "barrier", 2, now);
        AiJob merge = expanded.stream().filter(j -> j.stage() == ProcessingStage.MERGE).findFirst().orElseThrow();

        Optional<JobScheduler.ClaimedJob> claimedMerge = scheduler.claimNextForStage(now, ProcessingStage.MERGE);
        assertTrue(claimedMerge.isEmpty(), "merge must wait for extract barrier");

        for (AiJob extract : expanded) {
            if (extract.stage() == ProcessingStage.EXTRACT) {
                deps.markSatisfiedForCompletedDependency(extract.id());
            }
        }
        claimedMerge = scheduler.claimNextForStage(now, ProcessingStage.MERGE);
        assertTrue(claimedMerge.isPresent());
        assertEquals(merge.id(), claimedMerge.get().job().id());
        assertEquals(AiJobStatus.RUNNING, claimedMerge.get().job().status());
    }
}
