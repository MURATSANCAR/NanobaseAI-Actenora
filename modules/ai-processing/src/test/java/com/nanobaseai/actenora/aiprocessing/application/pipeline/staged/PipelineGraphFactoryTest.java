package com.nanobaseai.actenora.aiprocessing.application.pipeline.staged;

import com.nanobaseai.actenora.aiprocessing.application.port.AiJobRepository;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobStatus;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingStage;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryAiJobRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryProcessingJobDependencyRepository;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryOutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.support.TenantFairnessTracker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineGraphFactoryTest {

    @Test
    void admitCreatesNormalizeAndTriageWithDependency() {
        InMemoryProcessingJobDependencyRepository deps = new InMemoryProcessingJobDependencyRepository();
        AiJobRepository jobs = new InMemoryAiJobRepository(deps);
        StageCommandPublisher commands = new StageCommandPublisher(new InMemoryOutboxStore(new TenantFairnessTracker()));
        PipelineGraphFactory factory = new PipelineGraphFactory(jobs, deps, commands);

        UUID tenant = UUID.randomUUID();
        UUID meeting = UUID.randomUUID();
        UUID transcript = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-27T12:00:00Z");

        PipelineGraphFactory.GraphAdmission first = factory.admitFromTranscriptReady(
                tenant, meeting, transcript, "hash-1", JobPriority.NORMAL, "tr", 10,
                UUID.randomUUID(), now, Duration.ofHours(1));
        assertTrue(first.created());
        assertEquals(ProcessingStage.ROOT, first.root().stage());
        assertEquals(AiJobStatus.SUCCEEDED, first.root().status());
        assertEquals(ProcessingStage.NORMALIZE, first.normalize().stage());
        assertEquals(ProcessingStage.TRIAGE, first.triage().stage());
        assertEquals(1, deps.countUnsatisfied(first.triage().id()));

        PipelineGraphFactory.GraphAdmission second = factory.admitFromTranscriptReady(
                tenant, meeting, transcript, "hash-1", JobPriority.NORMAL, "tr", 10,
                UUID.randomUUID(), now, Duration.ofHours(1));
        assertFalse(second.created());
        assertEquals(first.root().id(), second.root().id());

        PipelineGraphFactory.GraphAdmission forced = factory.admitFromTranscriptReady(
                tenant, meeting, transcript, "hash-1", JobPriority.NORMAL, "tr", 10,
                UUID.randomUUID(), now, Duration.ofHours(1), true);
        assertTrue(forced.created());
        assertTrue(!forced.root().id().equals(first.root().id()));
        assertTrue(!forced.normalize().id().equals(first.normalize().id()));
        assertTrue(!forced.triage().id().equals(first.triage().id()));
        assertTrue(forced.normalize().idempotencyKey().endsWith(":normalize"));
        assertTrue(forced.triage().idempotencyKey().endsWith(":triage"));
        assertFalse(forced.normalize().idempotencyKey().equals(forced.root().idempotencyKey()));
    }

    @Test
    void extractBarrierKeepsMergePendingUntilAllChunksDone() {
        InMemoryProcessingJobDependencyRepository deps = new InMemoryProcessingJobDependencyRepository();
        AiJobRepository jobs = new InMemoryAiJobRepository(deps);
        StageCommandPublisher commands = new StageCommandPublisher(new InMemoryOutboxStore(new TenantFairnessTracker()));
        PipelineGraphFactory factory = new PipelineGraphFactory(jobs, deps, commands);

        UUID tenant = UUID.randomUUID();
        UUID meeting = UUID.randomUUID();
        UUID transcript = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-27T12:00:00Z");
        PipelineGraphFactory.GraphAdmission admission = factory.admitFromTranscriptReady(
                tenant, meeting, transcript, "h2", JobPriority.NORMAL, "tr", 20,
                UUID.randomUUID(), now, Duration.ofHours(1));

        var chunkJobs = factory.expandAfterTriageFullPath(admission.triage(), "h2", now);
        AiJob chunk = chunkJobs.getFirst();
        var expanded = factory.expandExtractGraph(chunk, "h2", 3, now);
        AiJob merge = expanded.stream().filter(j -> j.stage() == ProcessingStage.MERGE).findFirst().orElseThrow();
        assertEquals(3, deps.countUnsatisfied(merge.id()));

        AiJob extract0 = expanded.stream().filter(j -> j.stage() == ProcessingStage.EXTRACT && j.chunkIndex().orElse(-1) == 0).findFirst().orElseThrow();
        deps.markSatisfiedForCompletedDependency(extract0.id());
        assertEquals(2, deps.countUnsatisfied(merge.id()));
    }
}
