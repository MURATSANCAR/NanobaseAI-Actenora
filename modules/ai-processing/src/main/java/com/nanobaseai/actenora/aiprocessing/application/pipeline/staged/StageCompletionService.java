package com.nanobaseai.actenora.aiprocessing.application.pipeline.staged;

import com.nanobaseai.actenora.aiprocessing.application.AiJobService;
import com.nanobaseai.actenora.aiprocessing.application.port.AiJobRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.ProcessingArtifactRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.ProcessingJobDependencyRepository;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingArtifact;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingJobDependency;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingStage;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Completes a stage job, persists artifacts, satisfies DAG edges, and wakes dependents.
 */
public final class StageCompletionService {

    private final AiJobService jobService;
    private final AiJobRepository jobs;
    private final ProcessingJobDependencyRepository dependencies;
    private final ProcessingArtifactRepository artifacts;
    private final StageCommandPublisher commands;
    private final PipelineGraphFactory graphFactory;
    private final TranscriptHashResolver transcriptHashResolver;
    private final StageMetricsPort metrics;
    private final AtomicLong earlyExitTotal = new AtomicLong();

    public StageCompletionService(
            AiJobService jobService,
            AiJobRepository jobs,
            ProcessingJobDependencyRepository dependencies,
            ProcessingArtifactRepository artifacts,
            StageCommandPublisher commands,
            PipelineGraphFactory graphFactory,
            TranscriptHashResolver transcriptHashResolver
    ) {
        this(jobService, jobs, dependencies, artifacts, commands, graphFactory, transcriptHashResolver, StageMetricsPort.noop());
    }

    public StageCompletionService(
            AiJobService jobService,
            AiJobRepository jobs,
            ProcessingJobDependencyRepository dependencies,
            ProcessingArtifactRepository artifacts,
            StageCommandPublisher commands,
            PipelineGraphFactory graphFactory,
            TranscriptHashResolver transcriptHashResolver,
            StageMetricsPort metrics
    ) {
        this.jobService = Objects.requireNonNull(jobService, "jobService");
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts");
        this.commands = Objects.requireNonNull(commands, "commands");
        this.graphFactory = Objects.requireNonNull(graphFactory, "graphFactory");
        this.transcriptHashResolver = Objects.requireNonNull(transcriptHashResolver, "transcriptHashResolver");
        this.metrics = metrics == null ? StageMetricsPort.noop() : metrics;
    }

    /** Exposed for OTel adapters in the composition root. */
    public long earlyExitTotal() {
        return earlyExitTotal.get();
    }

    public void complete(StageExecutionResult result) {
        Objects.requireNonNull(result, "result");
        // Prefer wall-clock end over claim Instant so completed_at > started_at when latency > 0.
        Instant now = Instant.now();
        AiJob job = result.job();

        if (!result.succeeded()) {
            jobService.failAttempt(
                    job.id(),
                    result.latencyMs(),
                    result.retryable(),
                    result.errorCode() == null ? "STAGE_FAILED" : result.errorCode(),
                    result.errorMessage() == null ? "stage failed" : result.errorMessage(),
                    now
            );
            metrics.recordDuration(job.stage(), result.latencyMs(), false);
            if (!result.retryable()) {
                metrics.recordDlq(job.stage());
            }
            return;
        }

        if (result.artifactJson() != null && result.artifactType() != null) {
            String payload = sanitizeArtifactJson(result.artifactJson());
            artifacts.save(ProcessingArtifact.inlineJson(
                    job.tenantId(),
                    job.id(),
                    job.meetingOccurrenceId(),
                    result.artifactType(),
                    payload,
                    now
            ));
        }

        job.queuedAt();
        long queueWaitMs = Math.max(0L, java.time.Duration.between(job.queuedAt(), now).toMillis() - result.latencyMs());
        metrics.recordQueueWait(job.stage(), queueWaitMs);

        jobService.completeAttempt(
                job.id(),
                result.latencyMs(),
                result.inputTokens(),
                result.outputTokens(),
                now);
        dependencies.markSatisfiedForCompletedDependency(job.id());

        String hash = transcriptHashResolver.hashFor(job.tenantId(), job.transcriptId());
        expandGraphIfNeeded(job, result, hash, now);
        wakeNewlyEligible(job, now);
        metrics.recordDuration(job.stage(), result.latencyMs(), true);
        if (result.earlyExitInformational()) {
            earlyExitTotal.incrementAndGet();
            metrics.recordEarlyExit();
        }
    }

    private void expandGraphIfNeeded(AiJob job, StageExecutionResult result, String hash, Instant now) {
        if (job.stage() == ProcessingStage.TRIAGE) {
            if (result.earlyExitInformational()) {
                graphFactory.expandEarlyExitMinutes(job, hash, now);
            } else {
                graphFactory.expandAfterTriageFullPath(job, hash, now);
            }
            return;
        }
        if (job.stage() == ProcessingStage.CHUNK) {
            int chunkCount = parseChunkCount(result.artifactJson());
            graphFactory.expandExtractGraph(job, hash, chunkCount, now);
        }
    }

    private void wakeNewlyEligible(AiJob completed, Instant now) {
        List<ProcessingJobDependency> dependents = dependencies.findByDependsOnJobId(completed.id());
        for (ProcessingJobDependency dep : dependents) {
            if (dependencies.countUnsatisfied(dep.jobId()) > 0) {
                continue;
            }
            Optional<AiJob> ready = jobs.findById(dep.jobId());
            if (ready.isEmpty()) {
                continue;
            }
            AiJob next = ready.get();
            if (next.status().name().equals("QUEUED")) {
                commands.publishWakeup(
                        next.tenantId(),
                        next.id(),
                        next.meetingOccurrenceId(),
                        next.correlationId(),
                        next.stage(),
                        now
                );
            }
        }
    }

    private static int parseChunkCount(String artifactJson) {
        if (artifactJson == null || artifactJson.isBlank()) {
            return 1;
        }
        // {"chunkCount":N}
        int idx = artifactJson.indexOf("\"chunkCount\"");
        if (idx < 0) {
            return 1;
        }
        int colon = artifactJson.indexOf(':', idx);
        if (colon < 0) {
            return 1;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = colon + 1; i < artifactJson.length(); i++) {
            char c = artifactJson.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
            } else if (!digits.isEmpty()) {
                break;
            }
        }
        if (digits.isEmpty()) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(digits.toString()));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    /** Ensure artifact payloads are valid JSON so PG jsonb inserts never leave jobs RUNNING. */
    private static String sanitizeArtifactJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        String trimmed = raw.trim();
        try {
            MAPPER.readTree(trimmed);
            return trimmed;
        } catch (Exception ignored) {
            // fall through
        }
        com.fasterxml.jackson.databind.node.ObjectNode wrapper = MAPPER.createObjectNode();
        wrapper.put("truncated", true);
        wrapper.put("raw", trimmed.length() > 8_000 ? trimmed.substring(0, 8_000) : trimmed);
        return wrapper.toString();
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    @FunctionalInterface
    public interface TranscriptHashResolver {
        String hashFor(UUID tenantId, UUID transcriptId);
    }
}
