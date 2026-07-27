package com.nanobaseai.actenora.aiprocessing.application.pipeline.staged;

import com.nanobaseai.actenora.aiprocessing.application.port.AiJobRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.ProcessingJobDependencyRepository;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobStatus;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingJobDependency;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingStage;
import com.nanobaseai.actenora.aiprocessing.domain.job.SelectedRoute;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds the initial staged pipeline graph (ROOT + NORMALIZE + TRIAGE) and expands
 * after triage / chunk planning.
 */
public final class PipelineGraphFactory {

    public static final String PROMPT_NORMALIZE = "pv-meeting-normalize-v2";
    public static final String PROMPT_TRIAGE = "pv-meeting-triage-v2";
    public static final String PROMPT_CHUNK = "pv-meeting-chunk-plan-v2";
    public static final String PROMPT_EXTRACT = "pv-meeting-chunk-extraction-v2";
    public static final String PROMPT_MERGE = "pv-meeting-candidate-merge-v2";
    public static final String PROMPT_VALIDATE = "pv-meeting-validate-v2";
    public static final String PROMPT_MINUTES = "pv-meeting-final-note-v2";
    public static final String PROMPT_EMBED = "pv-meeting-embed-v1";
    public static final String SCHEMA_EXTRACTION = "extraction-output.v1";
    public static final String SCHEMA_TRIAGE = "meeting-triage.v1";
    public static final String SCHEMA_MINUTES = "final-note.v1";

    private final AiJobRepository jobs;
    private final ProcessingJobDependencyRepository dependencies;
    private final StageCommandPublisher commands;

    public PipelineGraphFactory(
            AiJobRepository jobs,
            ProcessingJobDependencyRepository dependencies,
            StageCommandPublisher commands
    ) {
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
        this.commands = Objects.requireNonNull(commands, "commands");
    }

    public record GraphAdmission(
            AiJob root,
            AiJob normalize,
            AiJob triage,
            boolean created
    ) {
    }

    public GraphAdmission admitFromTranscriptReady(
            UUID tenantId,
            UUID meetingOccurrenceId,
            UUID transcriptId,
            String transcriptHash,
            JobPriority priority,
            String language,
            int contextSize,
            UUID correlationId,
            Instant now,
            Duration deadline
    ) {
        String rootKey = "meeting:" + meetingOccurrenceId + ":root:" + transcriptHash + ":pv:v2";
        Optional<AiJob> existing = jobs.findByIdempotencyKey(tenantId, rootKey);
        if (existing.isPresent()) {
            AiJob root = existing.get();
            return new GraphAdmission(root, null, null, false);
        }

        Instant deadlineAt = now.plus(deadline == null ? Duration.ofHours(1) : deadline);
        AiJob root = AiJob.enqueueStaged(
                tenantId, meetingOccurrenceId, transcriptId,
                "PIPELINE_ROOT", ProcessingStage.ROOT, priority,
                AiCapability.TRANSCRIPT_EXTRACTION,
                PROMPT_NORMALIZE, SCHEMA_EXTRACTION, language, contextSize, true,
                now, deadlineAt, correlationId, null, rootKey, null
        );
        // ROOT is a coordinator — mark succeeded immediately (no worker claim).
        forceSucceeded(root, now);
        jobs.save(root);

        AiJob normalize = AiJob.enqueueStaged(
                tenantId, meetingOccurrenceId, transcriptId,
                "NORMALIZE", ProcessingStage.NORMALIZE, priority,
                AiCapability.TRANSCRIPT_EXTRACTION,
                PROMPT_NORMALIZE, SCHEMA_EXTRACTION, language, contextSize, true,
                now, deadlineAt, correlationId, root.id(),
                "meeting:" + meetingOccurrenceId + ":normalize:" + transcriptHash + ":pv:v2",
                null
        );
        applySyntheticRoute(normalize, now);
        jobs.save(normalize);

        AiJob triage = AiJob.enqueueStaged(
                tenantId, meetingOccurrenceId, transcriptId,
                "MEETING_TRIAGE", ProcessingStage.TRIAGE, priority,
                AiCapability.TRANSCRIPT_EXTRACTION,
                PROMPT_TRIAGE, SCHEMA_TRIAGE, language, contextSize, true,
                now, deadlineAt, correlationId, root.id(),
                "meeting:" + meetingOccurrenceId + ":triage:" + transcriptHash + ":pv:v2",
                null
        );
        applySyntheticRoute(triage, now);
        jobs.save(triage);
        link(triage.id(), normalize.id(), now);

        commands.publishWakeup(tenantId, normalize.id(), meetingOccurrenceId, correlationId, ProcessingStage.NORMALIZE, now);
        return new GraphAdmission(root, normalize, triage, true);
    }

    public List<AiJob> expandAfterTriageFullPath(
            AiJob triageJob,
            String transcriptHash,
            Instant now
    ) {
        UUID tenantId = triageJob.tenantId();
        UUID meetingId = triageJob.meetingOccurrenceId();
        UUID transcriptId = triageJob.transcriptId();
        UUID rootId = triageJob.parentJobId().orElse(triageJob.id());
        JobPriority priority = triageJob.priority();
        Instant deadlineAt = triageJob.deadlineAt();
        String language = triageJob.language();
        int contextSize = triageJob.contextSize();
        UUID correlationId = triageJob.correlationId();

        AiJob chunk = saveStaged(
                tenantId, meetingId, transcriptId, "CHUNK_PLAN", ProcessingStage.CHUNK,
                priority, AiCapability.TRANSCRIPT_EXTRACTION, PROMPT_CHUNK, SCHEMA_EXTRACTION,
                language, contextSize, now, deadlineAt, correlationId, rootId,
                "meeting:" + meetingId + ":chunk:" + transcriptHash + ":pv:v2", null
        );
        link(chunk.id(), triageJob.id(), now);
        commands.publishWakeup(tenantId, chunk.id(), meetingId, correlationId, ProcessingStage.CHUNK, now);
        return List.of(chunk);
    }

    public List<AiJob> expandExtractGraph(
            AiJob chunkJob,
            String transcriptHash,
            int chunkCount,
            Instant now
    ) {
        if (chunkCount < 1) {
            chunkCount = 1;
        }
        UUID tenantId = chunkJob.tenantId();
        UUID meetingId = chunkJob.meetingOccurrenceId();
        UUID transcriptId = chunkJob.transcriptId();
        UUID rootId = chunkJob.parentJobId().orElse(chunkJob.id());
        JobPriority priority = chunkJob.priority();
        Instant deadlineAt = chunkJob.deadlineAt();
        String language = chunkJob.language();
        int contextSize = chunkJob.contextSize();
        UUID correlationId = chunkJob.correlationId();

        List<AiJob> extracts = new ArrayList<>();
        for (int i = 0; i < chunkCount; i++) {
            AiJob extract = saveStaged(
                    tenantId, meetingId, transcriptId, "CHUNK_EXTRACTION", ProcessingStage.EXTRACT,
                    priority, AiCapability.TRANSCRIPT_EXTRACTION, PROMPT_EXTRACT, SCHEMA_EXTRACTION,
                    language, contextSize, now, deadlineAt, correlationId, rootId,
                    "meeting:" + meetingId + ":extract:" + transcriptHash + ":chunk:" + i + ":pv:v2",
                    i
            );
            link(extract.id(), chunkJob.id(), now);
            extracts.add(extract);
            commands.publishWakeup(tenantId, extract.id(), meetingId, correlationId, ProcessingStage.EXTRACT, now);
        }

        AiJob merge = saveStaged(
                tenantId, meetingId, transcriptId, "CANDIDATE_MERGE", ProcessingStage.MERGE,
                priority, AiCapability.CONTRADICTION_DETECTION, PROMPT_MERGE, SCHEMA_EXTRACTION,
                language, contextSize, now, deadlineAt, correlationId, rootId,
                "meeting:" + meetingId + ":merge:" + transcriptHash + ":pv:v2", null
        );
        for (AiJob extract : extracts) {
            link(merge.id(), extract.id(), now);
        }

        AiJob validate = saveStaged(
                tenantId, meetingId, transcriptId, "VALIDATION", ProcessingStage.VALIDATE,
                priority, AiCapability.VALIDATION, PROMPT_VALIDATE, SCHEMA_EXTRACTION,
                language, contextSize, now, deadlineAt, correlationId, rootId,
                "meeting:" + meetingId + ":validate:" + transcriptHash + ":pv:v2", null
        );
        link(validate.id(), merge.id(), now);

        AiJob minutes = saveStaged(
                tenantId, meetingId, transcriptId, "FINAL_NOTE", ProcessingStage.MINUTES,
                priority, AiCapability.FINAL_NOTE, PROMPT_MINUTES, SCHEMA_MINUTES,
                language, contextSize, now, deadlineAt, correlationId, rootId,
                "meeting:" + meetingId + ":minutes:" + transcriptHash + ":pv:v2", null
        );
        link(minutes.id(), validate.id(), now);

        List<AiJob> all = new ArrayList<>(extracts);
        all.add(merge);
        all.add(validate);
        all.add(minutes);
        return all;
    }

    public AiJob expandEarlyExitMinutes(AiJob triageJob, String transcriptHash, Instant now) {
        UUID rootId = triageJob.parentJobId().orElse(triageJob.id());
        AiJob minutes = saveStaged(
                triageJob.tenantId(),
                triageJob.meetingOccurrenceId(),
                triageJob.transcriptId(),
                "FINAL_NOTE",
                ProcessingStage.MINUTES,
                triageJob.priority(),
                AiCapability.FINAL_NOTE,
                PROMPT_MINUTES,
                SCHEMA_MINUTES,
                triageJob.language(),
                triageJob.contextSize(),
                now,
                triageJob.deadlineAt(),
                triageJob.correlationId(),
                rootId,
                "meeting:" + triageJob.meetingOccurrenceId() + ":minutes-lite:" + transcriptHash + ":pv:v2",
                null
        );
        link(minutes.id(), triageJob.id(), now);
        commands.publishWakeup(
                triageJob.tenantId(), minutes.id(), triageJob.meetingOccurrenceId(),
                triageJob.correlationId(), ProcessingStage.MINUTES, now);
        return minutes;
    }

    public AiJob admitEmbedding(
            UUID tenantId,
            UUID meetingOccurrenceId,
            UUID transcriptId,
            UUID noteVersionId,
            String language,
            Instant now
    ) {
        String key = "meeting:" + meetingOccurrenceId + ":embed:note:" + noteVersionId;
        Optional<AiJob> existing = jobs.findByIdempotencyKey(tenantId, key);
        if (existing.isPresent()) {
            return existing.get();
        }
        AiJob embed = saveStaged(
                tenantId, meetingOccurrenceId, transcriptId, "EMBEDDING", ProcessingStage.EMBEDDING,
                JobPriority.NORMAL, AiCapability.EMBEDDING, PROMPT_EMBED, "embedding.v1",
                language, 0, now, now.plus(Duration.ofHours(2)), noteVersionId, null, key, null
        );
        commands.publishWakeup(tenantId, embed.id(), meetingOccurrenceId, noteVersionId, ProcessingStage.EMBEDDING, now);
        return embed;
    }

    private AiJob saveStaged(
            UUID tenantId,
            UUID meetingId,
            UUID transcriptId,
            String taskType,
            ProcessingStage stage,
            JobPriority priority,
            AiCapability capability,
            String promptVersion,
            String schemaVersion,
            String language,
            int contextSize,
            Instant now,
            Instant deadlineAt,
            UUID correlationId,
            UUID parentJobId,
            String idempotencyKey,
            Integer chunkIndex
    ) {
        Optional<AiJob> existing = jobs.findByIdempotencyKey(tenantId, idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        AiJob job = AiJob.enqueueStaged(
                tenantId, meetingId, transcriptId, taskType, stage, priority, capability,
                promptVersion, schemaVersion, language, contextSize, true,
                now, deadlineAt, correlationId, parentJobId, idempotencyKey, chunkIndex
        );
        applySyntheticRoute(job, now);
        jobs.save(job);
        return job;
    }


    private void link(UUID jobId, UUID dependsOnJobId, Instant now) {
        ProcessingJobDependency dep = ProcessingJobDependency.pending(jobId, dependsOnJobId, now);
        Optional<AiJob> upstream = jobs.findById(dependsOnJobId);
        if (upstream.isPresent() && upstream.get().status() == AiJobStatus.SUCCEEDED) {
            dep.markSatisfied();
        }
        dependencies.save(dep);
    }

    private static void applySyntheticRoute(AiJob job, Instant now) {
        // Deterministic / embedding stages still need a route to markRunning.
        UUID modelId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID deploymentId = UUID.fromString("00000000-0000-4000-8000-000000000002");
        job.applyRoute(new SelectedRoute(
                modelId, deploymentId, "staged-local", "staged_pipeline_bootstrap", List.of(), now));
    }

    private static void forceSucceeded(AiJob root, Instant now) {
        UUID modelId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID deploymentId = UUID.fromString("00000000-0000-4000-8000-000000000002");
        root.applyRoute(new SelectedRoute(
                modelId, deploymentId, "staged-root", "root_coordinator", List.of(), now));
        root.markRunning(now);
        root.markSucceeded(0, 0, now);
    }
}
