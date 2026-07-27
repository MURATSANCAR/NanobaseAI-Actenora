package com.nanobaseai.actenora.aiprocessing.application.pipeline.staged;

import com.nanobaseai.actenora.aiprocessing.application.AiJobService;
import com.nanobaseai.actenora.aiprocessing.application.admission.DefaultAdmissionController;
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
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryProcessingArtifactRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryProcessingJobDependencyRepository;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryOutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.support.TenantFairnessTracker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end staged DAG choreography without Rabbit/LLM (in-memory claim + mock executors).
 */
class StageChoreographyTest {

    @Test
    void fullPathRunsNormalizeThroughMinutesWithExtractBarrier() {
        InMemoryProcessingJobDependencyRepository deps = new InMemoryProcessingJobDependencyRepository();
        InMemoryAiJobRepository jobs = new InMemoryAiJobRepository(deps);
        InMemoryAiAttemptRepository attempts = new InMemoryAiAttemptRepository();
        InMemoryProcessingArtifactRepository artifacts = new InMemoryProcessingArtifactRepository();
        StageCommandPublisher commands = new StageCommandPublisher(new InMemoryOutboxStore(new TenantFairnessTracker()));
        PipelineGraphFactory graph = new PipelineGraphFactory(jobs, deps, commands);

        TenantAiPolicyPort policy = permissivePolicy();
        ModelRouter router = successRouter();
        FairJobScheduler scheduler = new FairJobScheduler(jobs, attempts, policy, router);
        DefaultAdmissionController admission = new DefaultAdmissionController(jobs, policy, router, scheduler);
        AiJobService jobService = new AiJobService(admission, jobs, attempts, scheduler);

        AtomicInteger earlyExits = new AtomicInteger();
        StageMetricsPort metrics = new StageMetricsPort() {
            @Override
            public void recordDuration(ProcessingStage stage, long durationMs, boolean success) {
            }

            @Override
            public void recordQueueWait(ProcessingStage stage, long waitMs) {
            }

            @Override
            public void recordDlq(ProcessingStage stage) {
            }

            @Override
            public void recordEarlyExit() {
                earlyExits.incrementAndGet();
            }
        };

        StageCompletionService completion = new StageCompletionService(
                jobService, jobs, deps, artifacts, commands, graph,
                (t, id) -> "hash-choreo", metrics
        );

        Map<ProcessingStage, StageExecutor> executors = new EnumMap<>(ProcessingStage.class);
        executors.put(ProcessingStage.NORMALIZE, stub(ProcessingStage.NORMALIZE, """
                {"segmentCount":2}
                """, false));
        executors.put(ProcessingStage.TRIAGE, stub(ProcessingStage.TRIAGE, """
                {"containsDecisions":true,"containsActions":true,"containsRisks":false,"meetingType":"DECISION"}
                """, false));
        executors.put(ProcessingStage.CHUNK, stub(ProcessingStage.CHUNK, """
                {"chunkCount":2}
                """, false));
        executors.put(ProcessingStage.EXTRACT, new StageExecutor() {
            @Override
            public ProcessingStage stage() {
                return ProcessingStage.EXTRACT;
            }

            @Override
            public StageExecutionResult execute(AiJob job, Instant now) {
                return StageExecutionResult.success(
                        job,
                        "chunk-extraction-" + job.chunkIndex().orElse(0),
                        """
                                {"topics":[],"decisions":[],"actionItems":[],"risks":[],"openQuestions":[],"commitments":[],"qualityFlags":[],"evidenceSegmentIds":[],"confidence":0.0}
                                """.trim(),
                        1, 1, 5L, now
                );
            }
        });
        executors.put(ProcessingStage.MERGE, stub(ProcessingStage.MERGE, """
                {"topics":[],"decisions":[],"actionItems":[],"risks":[],"openQuestions":[],"commitments":[],"qualityFlags":[],"evidenceSegmentIds":[],"confidence":0.0}
                """, false));
        executors.put(ProcessingStage.VALIDATE, stub(ProcessingStage.VALIDATE, """
                {"topics":[],"decisions":[],"actionItems":[],"risks":[],"openQuestions":[],"commitments":[],"qualityFlags":[],"evidenceSegmentIds":[],"confidence":0.0}
                """, false));
        executors.put(ProcessingStage.MINUTES, stub(ProcessingStage.MINUTES, """
                {"executiveSummary":"ok","requiresManualReview":false}
                """, false));

        StagedPipelineRunner runner = new StagedPipelineRunner(scheduler, executors, completion);

        UUID tenant = UUID.randomUUID();
        UUID meeting = UUID.randomUUID();
        UUID transcript = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-27T15:00:00Z");
        graph.admitFromTranscriptReady(
                tenant, meeting, transcript, "hash-choreo", JobPriority.NORMAL, "tr", 4,
                UUID.randomUUID(), now, Duration.ofHours(1)
        );

        // Drain stage by stage until minutes succeeds.
        assertTrue(runner.runNext(ProcessingStage.NORMALIZE, now).isPresent());
        assertTrue(runner.runNext(ProcessingStage.TRIAGE, now.plusSeconds(1)).isPresent());
        assertTrue(runner.runNext(ProcessingStage.CHUNK, now.plusSeconds(2)).isPresent());

        // Merge must stay blocked until both extracts finish.
        assertTrue(runner.runNext(ProcessingStage.MERGE, now.plusSeconds(3)).isEmpty());
        assertTrue(runner.runNext(ProcessingStage.EXTRACT, now.plusSeconds(4)).isPresent());
        assertTrue(runner.runNext(ProcessingStage.MERGE, now.plusSeconds(5)).isEmpty());
        assertTrue(runner.runNext(ProcessingStage.EXTRACT, now.plusSeconds(6)).isPresent());
        assertTrue(runner.runNext(ProcessingStage.MERGE, now.plusSeconds(7)).isPresent());
        assertTrue(runner.runNext(ProcessingStage.VALIDATE, now.plusSeconds(8)).isPresent());
        Optional<StageExecutionResult> minutes = runner.runNext(ProcessingStage.MINUTES, now.plusSeconds(9));
        assertTrue(minutes.isPresent());
        assertTrue(minutes.get().succeeded());

        long succeeded = jobs.listByTenant(tenant).stream()
                .filter(j -> j.status() == AiJobStatus.SUCCEEDED)
                .count();
        assertTrue(succeeded >= 8, "root+normalize+triage+chunk+2extract+merge+validate+minutes");
        assertEquals(0, earlyExits.get());
    }

    @Test
    void informationalTriageSkipsExtractGraph() {
        InMemoryProcessingJobDependencyRepository deps = new InMemoryProcessingJobDependencyRepository();
        InMemoryAiJobRepository jobs = new InMemoryAiJobRepository(deps);
        InMemoryAiAttemptRepository attempts = new InMemoryAiAttemptRepository();
        InMemoryProcessingArtifactRepository artifacts = new InMemoryProcessingArtifactRepository();
        StageCommandPublisher commands = new StageCommandPublisher(new InMemoryOutboxStore(new TenantFairnessTracker()));
        PipelineGraphFactory graph = new PipelineGraphFactory(jobs, deps, commands);
        FairJobScheduler scheduler = new FairJobScheduler(jobs, attempts, permissivePolicy(), successRouter());
        AiJobService jobService = new AiJobService(
                new DefaultAdmissionController(jobs, permissivePolicy(), successRouter(), scheduler),
                jobs, attempts, scheduler
        );
        StageCompletionService completion = new StageCompletionService(
                jobService, jobs, deps, artifacts, commands, graph,
                (t, id) -> "hash-lite", StageMetricsPort.noop()
        );

        Map<ProcessingStage, StageExecutor> executors = new EnumMap<>(ProcessingStage.class);
        executors.put(ProcessingStage.NORMALIZE, stub(ProcessingStage.NORMALIZE, "{}", false));
        executors.put(ProcessingStage.TRIAGE, stub(
                ProcessingStage.TRIAGE,
                """
                        {"containsDecisions":false,"containsActions":false,"containsRisks":false,"meetingType":"INFORMATIONAL"}
                        """.trim(),
                true
        ));
        executors.put(ProcessingStage.MINUTES, stub(ProcessingStage.MINUTES, "{}", false));
        StagedPipelineRunner runner = new StagedPipelineRunner(scheduler, executors, completion);

        UUID tenant = UUID.randomUUID();
        Instant now = Instant.now();
        graph.admitFromTranscriptReady(
                tenant, UUID.randomUUID(), UUID.randomUUID(), "hash-lite", JobPriority.NORMAL, "tr", 1,
                UUID.randomUUID(), now, Duration.ofHours(1)
        );
        runner.runNext(ProcessingStage.NORMALIZE, now);
        runner.runNext(ProcessingStage.TRIAGE, now.plusSeconds(1));
        assertTrue(runner.runNext(ProcessingStage.EXTRACT, now.plusSeconds(2)).isEmpty());
        assertTrue(runner.runNext(ProcessingStage.MINUTES, now.plusSeconds(3)).isPresent());
        assertTrue(jobs.listByTenant(tenant).stream().noneMatch(j -> j.stage() == ProcessingStage.EXTRACT));
    }

    @Test
    void nonRetryableFailureRecordsDlqMetricAndBlocksMerge() {
        InMemoryProcessingJobDependencyRepository deps = new InMemoryProcessingJobDependencyRepository();
        InMemoryAiJobRepository jobs = new InMemoryAiJobRepository(deps);
        InMemoryAiAttemptRepository attempts = new InMemoryAiAttemptRepository();
        InMemoryProcessingArtifactRepository artifacts = new InMemoryProcessingArtifactRepository();
        StageCommandPublisher commands = new StageCommandPublisher(new InMemoryOutboxStore(new TenantFairnessTracker()));
        PipelineGraphFactory graph = new PipelineGraphFactory(jobs, deps, commands);
        FairJobScheduler scheduler = new FairJobScheduler(jobs, attempts, permissivePolicy(), successRouter());
        AiJobService jobService = new AiJobService(
                new DefaultAdmissionController(jobs, permissivePolicy(), successRouter(), scheduler),
                jobs, attempts, scheduler
        );

        AtomicInteger dlq = new AtomicInteger();
        StageMetricsPort metrics = new StageMetricsPort() {
            @Override
            public void recordDuration(ProcessingStage stage, long durationMs, boolean success) {
            }

            @Override
            public void recordQueueWait(ProcessingStage stage, long waitMs) {
            }

            @Override
            public void recordDlq(ProcessingStage stage) {
                if (stage == ProcessingStage.EXTRACT) {
                    dlq.incrementAndGet();
                }
            }

            @Override
            public void recordEarlyExit() {
            }
        };
        StageCompletionService completion = new StageCompletionService(
                jobService, jobs, deps, artifacts, commands, graph,
                (t, id) -> "hash-fail", metrics
        );

        Map<ProcessingStage, StageExecutor> executors = new EnumMap<>(ProcessingStage.class);
        executors.put(ProcessingStage.NORMALIZE, stub(ProcessingStage.NORMALIZE, "{\"segmentCount\":1}", false));
        executors.put(ProcessingStage.TRIAGE, stub(ProcessingStage.TRIAGE, """
                {"containsDecisions":true,"containsActions":false,"containsRisks":false,"meetingType":"DECISION"}
                """, false));
        executors.put(ProcessingStage.CHUNK, stub(ProcessingStage.CHUNK, "{\"chunkCount\":1}", false));
        executors.put(ProcessingStage.EXTRACT, new StageExecutor() {
            @Override
            public ProcessingStage stage() {
                return ProcessingStage.EXTRACT;
            }

            @Override
            public StageExecutionResult execute(AiJob job, Instant now) {
                return StageExecutionResult.failure(
                        job, false, "EXTRACT_POISON", "non-retryable", 2L, now);
            }
        });
        executors.put(ProcessingStage.MERGE, stub(ProcessingStage.MERGE, "{}", false));
        StagedPipelineRunner runner = new StagedPipelineRunner(scheduler, executors, completion);

        UUID tenant = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-27T16:00:00Z");
        graph.admitFromTranscriptReady(
                tenant, UUID.randomUUID(), UUID.randomUUID(), "hash-fail", JobPriority.NORMAL, "tr", 2,
                UUID.randomUUID(), now, Duration.ofHours(1)
        );
        runner.runNext(ProcessingStage.NORMALIZE, now);
        runner.runNext(ProcessingStage.TRIAGE, now.plusSeconds(1));
        runner.runNext(ProcessingStage.CHUNK, now.plusSeconds(2));
        Optional<StageExecutionResult> extract = runner.runNext(ProcessingStage.EXTRACT, now.plusSeconds(3));
        assertTrue(extract.isPresent());
        assertFalse(extract.get().succeeded());
        assertEquals(1, dlq.get());
        assertTrue(runner.runNext(ProcessingStage.MERGE, now.plusSeconds(4)).isEmpty());
    }

    @Test
    void embeddingStageIndexesViaApprovedKnowledgePort() {
        InMemoryProcessingJobDependencyRepository deps = new InMemoryProcessingJobDependencyRepository();
        InMemoryAiJobRepository jobs = new InMemoryAiJobRepository(deps);
        InMemoryAiAttemptRepository attempts = new InMemoryAiAttemptRepository();
        InMemoryProcessingArtifactRepository artifacts = new InMemoryProcessingArtifactRepository();
        StageCommandPublisher commands = new StageCommandPublisher(new InMemoryOutboxStore(new TenantFairnessTracker()));
        PipelineGraphFactory graph = new PipelineGraphFactory(jobs, deps, commands);
        FairJobScheduler scheduler = new FairJobScheduler(jobs, attempts, permissivePolicy(), successRouter());
        AiJobService jobService = new AiJobService(
                new DefaultAdmissionController(jobs, permissivePolicy(), successRouter(), scheduler),
                jobs, attempts, scheduler
        );
        StageCompletionService completion = new StageCompletionService(
                jobService, jobs, deps, artifacts, commands, graph,
                (t, id) -> "hash-embed", StageMetricsPort.noop()
        );

        AtomicInteger indexed = new AtomicInteger();
        Map<ProcessingStage, StageExecutor> executors = new EnumMap<>(ProcessingStage.class);
        executors.put(ProcessingStage.EMBEDDING, new DefaultStageExecutors.EmbeddingExecutor(
                (tenantId, meetingOccurrenceId, noteId, noteVersionId) -> {
                    indexed.incrementAndGet();
                }
        ));
        StagedPipelineRunner runner = new StagedPipelineRunner(scheduler, executors, completion);

        UUID tenant = UUID.randomUUID();
        UUID meeting = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID noteVersionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-27T17:00:00Z");
        graph.admitEmbedding(tenant, meeting, noteId, noteVersionId, "tr", now);
        Optional<StageExecutionResult> result = runner.runNext(ProcessingStage.EMBEDDING, now);
        assertTrue(result.isPresent());
        assertTrue(result.get().succeeded());
        assertEquals(1, indexed.get());
    }

    private static StageExecutor stub(ProcessingStage stage, String json, boolean early) {
        return new StageExecutor() {
            @Override
            public ProcessingStage stage() {
                return stage;
            }

            @Override
            public StageExecutionResult execute(AiJob job, Instant now) {
                if (early) {
                    return StageExecutionResult.earlyExit(job, json, 0, 0, 1L, now);
                }
                String type = switch (stage) {
                    case NORMALIZE -> "normalized";
                    case TRIAGE -> "triage";
                    case CHUNK -> "chunk-plan";
                    case MERGE -> "merged-bundle";
                    case VALIDATE -> "validated-bundle";
                    case MINUTES -> "final-minutes";
                    default -> stage.name().toLowerCase();
                };
                return StageExecutionResult.success(job, type, json, 0, 0, 1L, now);
            }
        };
    }

    private static TenantAiPolicyPort permissivePolicy() {
        return new TenantAiPolicyPort() {
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
                return 16;
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
    }

    private static ModelRouter successRouter() {
        return request -> ModelRouter.RouteResult.success(
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
    }
}
