package com.nanobaseai.actenora.aiprocessing.application.execution;

import com.nanobaseai.actenora.aiprocessing.application.AiJobService;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.InferenceResult;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.LocalModelProviderException;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderFailureCategory;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ResolvedInferenceInput;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.WorkerRequestEnvelope;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ExtractionPipelineService;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.PipelineRunRequest;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.PipelineRunResult;
import com.nanobaseai.actenora.aiprocessing.application.port.InferenceInputResolverPort;
import com.nanobaseai.actenora.aiprocessing.application.port.JobRoutingCoordinatorPort;
import com.nanobaseai.actenora.aiprocessing.application.port.JobRoutingCoordinatorPort.RoutedExecution;
import com.nanobaseai.actenora.aiprocessing.application.port.JobScheduler;
import com.nanobaseai.actenora.aiprocessing.application.port.LocalModelProvider;
import com.nanobaseai.actenora.aiprocessing.application.port.LocalModelProviderLocator;
import com.nanobaseai.actenora.aiprocessing.application.port.MeetingNoteHandoffPort;
import com.nanobaseai.actenora.aiprocessing.application.port.ServedModelResolverPort;
import com.nanobaseai.actenora.aiprocessing.application.port.TranscriptSegmentSourcePort;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobException;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobStatus;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FailureCategory;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.routing.InferenceTaskType;
import com.nanobaseai.actenora.aiprocessing.infrastructure.prompt.InMemoryPromptRegistry;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs a claimed AI job and closes the attempt.
 *
 * <p>Extraction tasks ({@link InferenceTaskType#CHUNK_EXTRACTION}) go through the FAZ 14
 * pipeline (chunk → schema validate → repair → merge). Other tasks keep the FAZ 13
 * single-shot provider path.
 *
 * <p>When a {@link JobRoutingCoordinatorPort} is configured, the FAZ 15 role-based router decides
 * the deployment at claim time and receives the attempt outcome for provenance and quality metrics.
 *
 * <p>When a {@link MeetingNoteHandoffPort} is configured, successful extraction drafts are mapped
 * into Meeting Intelligence corporate note objects at the composition root.
 */
public final class AiJobInferenceExecutor {

    public static final int DEFAULT_MAX_ATTEMPTS = 3;
    public static final int DEFAULT_MAX_TIMEOUT_SECONDS = 600;

    private final AiJobService jobService;
    private final LocalModelProviderLocator providers;
    private final InferenceInputResolverPort inputResolver;
    private final ServedModelResolverPort servedModels;
    private final ExtractionPipelineService extractionPipeline;
    private final TranscriptSegmentSourcePort segmentSource;
    private final JobRoutingCoordinatorPort routingCoordinator;
    private final MeetingNoteHandoffPort noteHandoff;
    private final int maxAttempts;
    private final int maxTimeoutSeconds;

    public AiJobInferenceExecutor(
            AiJobService jobService,
            LocalModelProviderLocator providers,
            InferenceInputResolverPort inputResolver,
            ServedModelResolverPort servedModels
    ) {
        this(
                jobService,
                providers,
                inputResolver,
                servedModels,
                null,
                TranscriptSegmentSourcePort.empty(),
                DEFAULT_MAX_ATTEMPTS,
                DEFAULT_MAX_TIMEOUT_SECONDS
        );
    }

    public AiJobInferenceExecutor(
            AiJobService jobService,
            LocalModelProviderLocator providers,
            InferenceInputResolverPort inputResolver,
            ServedModelResolverPort servedModels,
            int maxAttempts,
            int maxTimeoutSeconds
    ) {
        this(
                jobService,
                providers,
                inputResolver,
                servedModels,
                null,
                TranscriptSegmentSourcePort.empty(),
                maxAttempts,
                maxTimeoutSeconds
        );
    }

    public AiJobInferenceExecutor(
            AiJobService jobService,
            LocalModelProviderLocator providers,
            InferenceInputResolverPort inputResolver,
            ServedModelResolverPort servedModels,
            ExtractionPipelineService extractionPipeline,
            TranscriptSegmentSourcePort segmentSource,
            int maxAttempts,
            int maxTimeoutSeconds
    ) {
        this(
                jobService,
                providers,
                inputResolver,
                servedModels,
                extractionPipeline,
                segmentSource,
                null,
                maxAttempts,
                maxTimeoutSeconds
        );
    }

    public AiJobInferenceExecutor(
            AiJobService jobService,
            LocalModelProviderLocator providers,
            InferenceInputResolverPort inputResolver,
            ServedModelResolverPort servedModels,
            ExtractionPipelineService extractionPipeline,
            TranscriptSegmentSourcePort segmentSource,
            JobRoutingCoordinatorPort routingCoordinator,
            int maxAttempts,
            int maxTimeoutSeconds
    ) {
        this(
                jobService,
                providers,
                inputResolver,
                servedModels,
                extractionPipeline,
                segmentSource,
                routingCoordinator,
                null,
                maxAttempts,
                maxTimeoutSeconds
        );
    }

    public AiJobInferenceExecutor(
            AiJobService jobService,
            LocalModelProviderLocator providers,
            InferenceInputResolverPort inputResolver,
            ServedModelResolverPort servedModels,
            ExtractionPipelineService extractionPipeline,
            TranscriptSegmentSourcePort segmentSource,
            JobRoutingCoordinatorPort routingCoordinator,
            MeetingNoteHandoffPort noteHandoff,
            int maxAttempts,
            int maxTimeoutSeconds
    ) {
        this.jobService = Objects.requireNonNull(jobService, "jobService");
        this.providers = Objects.requireNonNull(providers, "providers");
        this.inputResolver = Objects.requireNonNull(inputResolver, "inputResolver");
        this.servedModels = Objects.requireNonNull(servedModels, "servedModels");
        this.extractionPipeline = extractionPipeline;
        this.segmentSource = Objects.requireNonNull(segmentSource, "segmentSource");
        this.routingCoordinator = routingCoordinator;
        this.noteHandoff = noteHandoff;
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (maxTimeoutSeconds < 1) {
            throw new IllegalArgumentException("maxTimeoutSeconds must be >= 1");
        }
        this.maxAttempts = maxAttempts;
        this.maxTimeoutSeconds = maxTimeoutSeconds;
    }

    public Optional<ExecutionOutcome> executeNext(Instant now) {
        Objects.requireNonNull(now, "now");
        return jobService.claimNext(now).map(claimed -> execute(claimed, now));
    }

    public int drain(int maxJobs, Instant now) {
        int executed = 0;
        for (int i = 0; i < maxJobs; i++) {
            if (executeNext(now).isEmpty()) {
                break;
            }
            executed++;
        }
        return executed;
    }

    public ExecutionOutcome execute(JobScheduler.ClaimedJob claimed, Instant now) {
        Objects.requireNonNull(claimed, "claimed");
        Objects.requireNonNull(now, "now");

        AiJob job = claimed.job();
        UUID attemptId = claimed.attempt().id();
        InferenceTaskType taskType = taskTypeOf(job);

        RoutedExecution routed = routingCoordinator == null
                ? null
                : routingCoordinator.routeForExecution(job, taskType);
        if (routed != null && !routed.hasProductionRoute()) {
            return failWithoutRoute(job, attemptId, routed, now);
        }

        ExecutionOutcome outcome = usesExtractionPipeline(taskType)
                ? executeExtraction(job, attemptId, now)
                : executeDirectProvider(job, attemptId, taskType, routed, now);
        recordRoutingOutcome(routed, outcome);
        return outcome;
    }

    private ExecutionOutcome failWithoutRoute(
            AiJob job,
            UUID attemptId,
            RoutedExecution routed,
            Instant now
    ) {
        boolean retryable = routed.requiresRetryQueue()
                && !routed.requiresManualReview()
                && job.attemptCount() < maxAttempts;
        AiJob failed = jobService.failAttempt(
                job.id(),
                0L,
                retryable,
                ProviderFailureCategory.HEALTH_DEGRADED.name(),
                routed.reason(),
                now
        );
        return ExecutionOutcome.failed(
                failed.id(),
                attemptId,
                failed.status(),
                0L,
                ProviderFailureCategory.HEALTH_DEGRADED,
                retryable
        );
    }

    private void recordRoutingOutcome(RoutedExecution routed, ExecutionOutcome outcome) {
        if (routed == null) {
            return;
        }
        if (outcome.succeeded()) {
            routingCoordinator.recordSuccess(routed, outcome.latencyMs(), true);
            return;
        }
        ProviderFailureCategory category = outcome.failureCategory() == null
                ? ProviderFailureCategory.UNKNOWN
                : outcome.failureCategory();
        routingCoordinator.recordFailure(
                routed,
                outcome.latencyMs(),
                category.name(),
                outcome.retryable() ? "retryable" : "permanent"
        );
    }

    private ExecutionOutcome executeExtraction(AiJob job, UUID attemptId, Instant now) {
        if (extractionPipeline == null) {
            return fail(
                    job,
                    attemptId,
                    0L,
                    ProviderFailureCategory.UNKNOWN,
                    false,
                    "extraction_pipeline_not_configured",
                    now
            );
        }

        long startedNanos = System.nanoTime();
        List<SegmentInput> segments = segmentSource.segmentsFor(
                TenantId.of(job.tenantId()), job.transcriptId());
        if (segments.isEmpty()) {
            return failPipeline(
                    job,
                    attemptId,
                    elapsedMs(startedNanos),
                    FailureCategory.EVIDENCE_MISSING,
                    "No transcript segments for " + job.transcriptId(),
                    true,
                    now
            );
        }

        String promptId = resolveExtractionPromptId(job);

        PipelineRunResult result = extractionPipeline.run(new PipelineRunRequest(
                TenantId.of(job.tenantId()),
                job.transcriptId(),
                job.meetingOccurrenceId(),
                promptId,
                segments,
                job.language(),
                timeoutSecondsFor(job, now)
        ));

        if (result.success()) {
            int inputTokens = (int) Math.min(Integer.MAX_VALUE, result.metrics().inputTokens());
            int outputTokens = (int) Math.min(Integer.MAX_VALUE, result.metrics().outputTokens());
            long latencyMs = result.metrics().durationMs() > 0
                    ? result.metrics().durationMs()
                    : elapsedMs(startedNanos);
            AiJob completed = jobService.completeAttempt(
                    job.id(), latencyMs, inputTokens, outputTokens, now);
            UUID meetingNoteId = null;
            try {
                meetingNoteId = handoffFinalNote(job, result).orElse(null);
            } catch (RuntimeException ex) {
                // Job already succeeded; handoff failures must not unwind the attempt.
            }
            return ExecutionOutcome.succeeded(
                    completed.id(), attemptId, completed.status(), latencyMs, meetingNoteId);
        }

        return failPipeline(
                job,
                attemptId,
                result.metrics().durationMs() > 0
                        ? result.metrics().durationMs()
                        : elapsedMs(startedNanos),
                result.failureCategory(),
                result.failureMessage() == null ? result.failureCategory().name() : result.failureMessage(),
                result.permanentFailure(),
                now
        );
    }

    private ExecutionOutcome executeDirectProvider(
            AiJob job,
            UUID attemptId,
            InferenceTaskType taskType,
            RoutedExecution routed,
            Instant now
    ) {
        UUID deploymentId = Optional.ofNullable(routed)
                .flatMap(RoutedExecution::deploymentId)
                .or(job::selectedDeploymentId)
                .orElseThrow(() -> AiJobException.invalidTransition("Claimed job has no deployment"));
        UUID modelDefinitionId = Optional.ofNullable(routed)
                .flatMap(RoutedExecution::modelDefinitionId)
                .or(job::selectedModelId)
                .orElseThrow(() -> AiJobException.invalidTransition("Claimed job has no model"));

        WorkerRequestEnvelope envelope = WorkerRequestEnvelope.builder()
                .jobId(job.id())
                .attemptId(attemptId)
                .taskType(taskType)
                .modelId(modelDefinitionId)
                .servedModelId(servedModelId(job, modelDefinitionId, routed))
                .promptVersion(job.promptVersion())
                .schemaVersion(job.schemaVersion())
                .timeoutSeconds(timeoutSecondsFor(job, now))
                .inputReference(inputReference(job))
                .build();

        LocalModelProvider provider = providers.providerFor(deploymentId);
        ResolvedInferenceInput input = inputResolver.resolve(job, taskType);

        long startedNanos = System.nanoTime();
        try {
            InferenceResult result = provider.submitInference(envelope, input);
            AiJob completed = jobService.completeAttempt(
                    job.id(),
                    result.latencyMs(),
                    result.tokenUsage().inputTokens(),
                    result.tokenUsage().outputTokens(),
                    now
            );
            return ExecutionOutcome.succeeded(
                    completed.id(), attemptId, completed.status(), result.latencyMs(), null);
        } catch (LocalModelProviderException ex) {
            return fail(job, attemptId, elapsedMs(startedNanos), ex.category(), ex.retryable(), ex.getMessage(), now);
        } catch (RuntimeException ex) {
            return fail(job, attemptId, elapsedMs(startedNanos),
                    ProviderFailureCategory.UNKNOWN, false, ex.getClass().getSimpleName(), now);
        }
    }

    private ExecutionOutcome failPipeline(
            AiJob job,
            UUID attemptId,
            long latencyMs,
            FailureCategory category,
            String detailSafe,
            boolean permanentFailure,
            Instant now
    ) {
        ProviderFailureCategory mapped = PipelineFailureMapper.toProviderCategory(category);
        boolean retryable = !permanentFailure
                && !isPermanent(mapped)
                && job.attemptCount() < maxAttempts;
        // Prefer the pipeline category name for attempt audit when available.
        String storedCategory = category == null ? mapped.name() : category.name();
        AiJob failed = jobService.failAttempt(
                job.id(), latencyMs, retryable, storedCategory, detailSafe, now);
        return ExecutionOutcome.failed(failed.id(), attemptId, failed.status(), latencyMs, mapped, retryable);
    }

    private ExecutionOutcome fail(
            AiJob job,
            UUID attemptId,
            long latencyMs,
            ProviderFailureCategory category,
            boolean providerRetryable,
            String detailSafe,
            Instant now
    ) {
        boolean retryable = providerRetryable
                && !isPermanent(category)
                && job.attemptCount() < maxAttempts;
        AiJob failed = jobService.failAttempt(
                job.id(), latencyMs, retryable, category.name(), detailSafe, now);
        return ExecutionOutcome.failed(failed.id(), attemptId, failed.status(), latencyMs, category, retryable);
    }

    private Optional<UUID> handoffFinalNote(AiJob job, PipelineRunResult result) {
        if (noteHandoff == null) {
            return Optional.empty();
        }
        return result.finalNoteOptional().flatMap(draft -> noteHandoff.handoff(
                new MeetingNoteHandoffPort.HandoffCommand(
                        job.tenantId(),
                        job.meetingOccurrenceId(),
                        job.transcriptId(),
                        job.id(),
                        result.modelVersion(),
                        result.promptVersionId(),
                        job.schemaVersion(),
                        draft
                )));
    }

    private static boolean usesExtractionPipeline(InferenceTaskType taskType) {
        return taskType == InferenceTaskType.CHUNK_EXTRACTION;
    }

    private static String resolveExtractionPromptId(AiJob job) {
        String version = job.promptVersion();
        if (version != null && version.startsWith("meeting.")) {
            return version;
        }
        return InMemoryPromptRegistry.DEFAULT_EXTRACTION_PROMPT_ID;
    }

    private static boolean isPermanent(ProviderFailureCategory category) {
        return switch (category) {
            case MODEL_MISMATCH,
                 INVALID_SERVED_MODEL,
                 MALFORMED_RESPONSE,
                 STREAMING_NOT_SUPPORTED,
                 CANCELLED,
                 UNKNOWN -> true;
            default -> false;
        };
    }

    private String servedModelId(AiJob job, UUID modelDefinitionId, RoutedExecution routed) {
        return servedModels.findServedModelId(modelDefinitionId)
                .or(() -> Optional.ofNullable(routed).flatMap(RoutedExecution::modelKey))
                .or(() -> job.selectedRoute().map(route -> route.modelKey()))
                .orElseThrow(() -> AiJobException.invalidTransition(
                        "No served model id for model " + modelDefinitionId));
    }

    private int timeoutSecondsFor(AiJob job, Instant now) {
        long remaining = Duration.between(now, job.deadlineAt()).toSeconds();
        if (remaining < 1) {
            return 1;
        }
        return (int) Math.min(remaining, maxTimeoutSeconds);
    }

    private static Map<String, Object> inputReference(AiJob job) {
        Map<String, Object> reference = new LinkedHashMap<>();
        reference.put("meetingOccurrenceId", job.meetingOccurrenceId().toString());
        reference.put("transcriptId", job.transcriptId().toString());
        reference.put("language", job.language());
        reference.put("contextSize", job.contextSize());
        reference.put("attempt", job.attemptCount());
        return reference;
    }

    static InferenceTaskType taskTypeOf(AiJob job) {
        for (InferenceTaskType candidate : InferenceTaskType.values()) {
            if (candidate.name().equalsIgnoreCase(job.taskType())) {
                return candidate;
            }
        }
        return fromCapability(job.requestedCapability());
    }

    private static InferenceTaskType fromCapability(AiCapability capability) {
        return switch (capability) {
            case TRANSCRIPT_EXTRACTION -> InferenceTaskType.CHUNK_EXTRACTION;
            case VALIDATION -> InferenceTaskType.VALIDATION;
            default -> InferenceTaskType.FINAL_NOTE;
        };
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    public record ExecutionOutcome(
            UUID jobId,
            UUID attemptId,
            AiJobStatus jobStatus,
            boolean succeeded,
            long latencyMs,
            ProviderFailureCategory failureCategory,
            boolean retryable,
            UUID meetingNoteId
    ) {
        static ExecutionOutcome succeeded(UUID jobId, UUID attemptId, AiJobStatus status, long latencyMs) {
            return succeeded(jobId, attemptId, status, latencyMs, null);
        }

        static ExecutionOutcome succeeded(
                UUID jobId,
                UUID attemptId,
                AiJobStatus status,
                long latencyMs,
                UUID meetingNoteId
        ) {
            return new ExecutionOutcome(jobId, attemptId, status, true, latencyMs, null, false, meetingNoteId);
        }

        static ExecutionOutcome failed(
                UUID jobId,
                UUID attemptId,
                AiJobStatus status,
                long latencyMs,
                ProviderFailureCategory category,
                boolean retryable
        ) {
            return new ExecutionOutcome(jobId, attemptId, status, false, latencyMs, category, retryable, null);
        }

        public Optional<ProviderFailureCategory> failure() {
            return Optional.ofNullable(failureCategory);
        }

        public Optional<UUID> meetingNoteIdOptional() {
            return Optional.ofNullable(meetingNoteId);
        }
    }
}
