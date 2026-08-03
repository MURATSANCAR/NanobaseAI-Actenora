package com.nanobaseai.actenora.aiprocessing.application.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobaseai.actenora.aiprocessing.application.port.PipelineQualityMetricsPort;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.extraction.ProposalCuePostProcessor;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.filter.CrossTypeMeetingItemScrubber;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.action.ActionPostProcessingPipeline;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.action.ExplicitActionCueRecoverer;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency.ActionContextualEnricher;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency.CrossTypeConsistencyAuditor;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency.CrossTypeMeetingItemSubsumer;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.note.FinalNoteConfidencePolicy;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ChunkingConfig;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ContextWindowGuard;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DeterministicExtractionValidator;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.EvidenceNearMissConfig;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.EvidenceReferenceScrubber;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionMerger;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FailureCategory;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteAssembler;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.MeetingLlmBudgets;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.PipelineException;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.PipelineRunMetrics;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.PipelineStage;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.PromptInjectionGuard;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.RetryClassifier;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.RetryDecision;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentNormalizer;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TranscriptChunk;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TranscriptChunker;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal.ChunkContext;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal.ChunkExtractionResult;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal.ChunkExtractionService;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal.ChunkSignalFeatureExtractor;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal.ChunkSignalSummary;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal.SignalGateConfig;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal.StructuralChunkSignalFeatureExtractor;
import com.nanobaseai.actenora.aiprocessing.domain.prompt.ExtractionPromptRules;
import com.nanobaseai.actenora.aiprocessing.domain.prompt.PublishedPrompt;
import com.nanobaseai.actenora.aiprocessing.domain.routing.InferenceTaskType;
import com.nanobaseai.actenora.aiprocessing.infrastructure.json.ExtractionBundleMapper;
import com.nanobaseai.actenora.aiprocessing.infrastructure.json.ExtractionJsonSchemaValidator;
import com.nanobaseai.actenora.aiprocessing.infrastructure.json.LimitedJsonRepair;
import com.nanobaseai.actenora.aiprocessing.infrastructure.json.PartialExtractionJsonRecovery;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Production extraction pipeline:
 * normalize → chunk → extract → merge → deterministic validate → final note.
 * Model identity stays behind {@link ModelRuntimePort}; no cloud fallback.
 */
public final class ExtractionPipelineService {

    private static final Logger LOG = Logger.getLogger(ExtractionPipelineService.class.getName());

    private final PromptRegistryPort promptRegistry;
    private final ModelRuntimePort modelRuntime;
    private final SegmentNormalizer normalizer;
    private final TranscriptChunker chunker;
    private final ContextWindowGuard contextWindowGuard;
    private final LimitedJsonRepair jsonRepair;
    private final ExtractionJsonSchemaValidator schemaValidator;
    private final ExtractionBundleMapper bundleMapper;
    private final DeterministicExtractionValidator deterministicValidator;
    private final ExtractionMerger merger;
    private final FinalNoteAssembler finalNoteAssembler;
    private final RetryClassifier retryClassifier;
    private final PromptInjectionGuard promptInjectionGuard;
    private final ChunkExtractionService chunkExtractionService;
    private final ChunkSignalFeatureExtractor signalFeatureExtractor;
    private final SignalGateConfig signalGateConfig;
    private final EvidenceReferenceScrubber evidenceScrubber;
    private final PartialExtractionJsonRecovery partialJsonRecovery;
    private final PipelineQualityMetricsPort qualityMetrics;
    private final MinutesFinalizationPolicy finalizationPolicy;
    /** Meeting start ISO for prompt {{meetingDate}}; set per {@link #run}. */
    private volatile String meetingDateForPrompt = "";

    public ExtractionPipelineService(
            PromptRegistryPort promptRegistry,
            ModelRuntimePort modelRuntime,
            SegmentNormalizer normalizer,
            TranscriptChunker chunker,
            ContextWindowGuard contextWindowGuard,
            LimitedJsonRepair jsonRepair,
            ExtractionJsonSchemaValidator schemaValidator,
            ExtractionBundleMapper bundleMapper,
            DeterministicExtractionValidator deterministicValidator,
            ExtractionMerger merger,
            FinalNoteAssembler finalNoteAssembler,
            RetryClassifier retryClassifier,
            PromptInjectionGuard promptInjectionGuard
    ) {
        this(
                promptRegistry,
                modelRuntime,
                normalizer,
                chunker,
                contextWindowGuard,
                jsonRepair,
                schemaValidator,
                bundleMapper,
                deterministicValidator,
                merger,
                finalNoteAssembler,
                retryClassifier,
                promptInjectionGuard,
                ChunkExtractionService.createDefault(),
                PipelineQualityMetricsPort.noop(),
                MinutesFinalizationPolicy.compatibility()
        );
    }

    public ExtractionPipelineService(
            PromptRegistryPort promptRegistry,
            ModelRuntimePort modelRuntime,
            SegmentNormalizer normalizer,
            TranscriptChunker chunker,
            ContextWindowGuard contextWindowGuard,
            LimitedJsonRepair jsonRepair,
            ExtractionJsonSchemaValidator schemaValidator,
            ExtractionBundleMapper bundleMapper,
            DeterministicExtractionValidator deterministicValidator,
            ExtractionMerger merger,
            FinalNoteAssembler finalNoteAssembler,
            RetryClassifier retryClassifier,
            PromptInjectionGuard promptInjectionGuard,
            ChunkExtractionService chunkExtractionService
    ) {
        this(
                promptRegistry,
                modelRuntime,
                normalizer,
                chunker,
                contextWindowGuard,
                jsonRepair,
                schemaValidator,
                bundleMapper,
                deterministicValidator,
                merger,
                finalNoteAssembler,
                retryClassifier,
                promptInjectionGuard,
                chunkExtractionService,
                PipelineQualityMetricsPort.noop(),
                MinutesFinalizationPolicy.compatibility()
        );
    }

    public ExtractionPipelineService(
            PromptRegistryPort promptRegistry,
            ModelRuntimePort modelRuntime,
            SegmentNormalizer normalizer,
            TranscriptChunker chunker,
            ContextWindowGuard contextWindowGuard,
            LimitedJsonRepair jsonRepair,
            ExtractionJsonSchemaValidator schemaValidator,
            ExtractionBundleMapper bundleMapper,
            DeterministicExtractionValidator deterministicValidator,
            ExtractionMerger merger,
            FinalNoteAssembler finalNoteAssembler,
            RetryClassifier retryClassifier,
            PromptInjectionGuard promptInjectionGuard,
            ChunkExtractionService chunkExtractionService,
            PipelineQualityMetricsPort qualityMetrics
    ) {
        this(
                promptRegistry,
                modelRuntime,
                normalizer,
                chunker,
                contextWindowGuard,
                jsonRepair,
                schemaValidator,
                bundleMapper,
                deterministicValidator,
                merger,
                finalNoteAssembler,
                retryClassifier,
                promptInjectionGuard,
                chunkExtractionService,
                qualityMetrics,
                MinutesFinalizationPolicy.compatibility()
        );
    }

    public ExtractionPipelineService(
            PromptRegistryPort promptRegistry,
            ModelRuntimePort modelRuntime,
            SegmentNormalizer normalizer,
            TranscriptChunker chunker,
            ContextWindowGuard contextWindowGuard,
            LimitedJsonRepair jsonRepair,
            ExtractionJsonSchemaValidator schemaValidator,
            ExtractionBundleMapper bundleMapper,
            DeterministicExtractionValidator deterministicValidator,
            ExtractionMerger merger,
            FinalNoteAssembler finalNoteAssembler,
            RetryClassifier retryClassifier,
            PromptInjectionGuard promptInjectionGuard,
            ChunkExtractionService chunkExtractionService,
            PipelineQualityMetricsPort qualityMetrics,
            MinutesFinalizationPolicy finalizationPolicy
    ) {
        this.promptRegistry = Objects.requireNonNull(promptRegistry, "promptRegistry");
        this.modelRuntime = Objects.requireNonNull(modelRuntime, "modelRuntime");
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
        this.chunker = Objects.requireNonNull(chunker, "chunker");
        this.contextWindowGuard = Objects.requireNonNull(contextWindowGuard, "contextWindowGuard");
        this.jsonRepair = Objects.requireNonNull(jsonRepair, "jsonRepair");
        this.schemaValidator = Objects.requireNonNull(schemaValidator, "schemaValidator");
        this.bundleMapper = Objects.requireNonNull(bundleMapper, "bundleMapper");
        this.deterministicValidator = Objects.requireNonNull(deterministicValidator, "deterministicValidator");
        this.merger = Objects.requireNonNull(merger, "merger");
        this.finalNoteAssembler = Objects.requireNonNull(finalNoteAssembler, "finalNoteAssembler");
        this.retryClassifier = Objects.requireNonNull(retryClassifier, "retryClassifier");
        this.promptInjectionGuard = Objects.requireNonNull(promptInjectionGuard, "promptInjectionGuard");
        this.chunkExtractionService = Objects.requireNonNull(chunkExtractionService, "chunkExtractionService");
        this.signalGateConfig = chunkExtractionService.config();
        this.signalFeatureExtractor = new StructuralChunkSignalFeatureExtractor(signalGateConfig);
        this.evidenceScrubber = new EvidenceReferenceScrubber(EvidenceNearMissConfig.load());
        this.partialJsonRecovery = new PartialExtractionJsonRecovery();
        this.qualityMetrics = qualityMetrics == null ? PipelineQualityMetricsPort.noop() : qualityMetrics;
        this.finalizationPolicy = Objects.requireNonNull(finalizationPolicy, "finalizationPolicy");
        this.meetingDateForPrompt = "";
    }

    public static ExtractionPipelineService create(
            PromptRegistryPort promptRegistry,
            ModelRuntimePort modelRuntime
    ) {
        // Gate disabled: unit tests assert extraction/validation behaviour on short fixtures.
        // Production Spring wiring uses ChunkExtractionService.createDefault() (gate on).
        return new ExtractionPipelineService(
                promptRegistry,
                modelRuntime,
                new SegmentNormalizer(),
                new TranscriptChunker(),
                new ContextWindowGuard(),
                new LimitedJsonRepair(),
                new ExtractionJsonSchemaValidator(),
                new ExtractionBundleMapper(),
                new DeterministicExtractionValidator(),
                new ExtractionMerger(),
                new FinalNoteAssembler(),
                new RetryClassifier(),
                new PromptInjectionGuard(),
                ChunkExtractionService.create(SignalGateConfig.productionDefaults().withEnabled(false))
        );
    }

    public PipelineRunResult run(PipelineRunRequest request) {
        Objects.requireNonNull(request, "request");
        PipelineRunMetrics metrics = new PipelineRunMetrics();
        long pipelineStarted = System.nanoTime();

        PublishedPrompt prompt = promptRegistry.requirePublished(request.promptId());
        String promptVersionId = prompt.promptVersionId();
        ModelDescriptor descriptor = modelRuntime.descriptor();
        String modelVersion = descriptor.modelVersion();

        try {
            if (!modelRuntime.healthy()) {
                throw new PipelineException(
                        FailureCategory.MODEL_UNAVAILABLE,
                        PipelineStage.EXTRACT,
                        "Model runtime unhealthy"
                );
            }

            List<SegmentInput> normalized = normalizer.normalize(request.segments());
            this.meetingDateForPrompt = request.meetingStartedAtIso() == null ? "" : request.meetingStartedAtIso();
            ChunkingConfig chunkingConfig = ChunkingConfig.productionDefaults(descriptor.contextWindowTokens())
                    .withMaxOutput(MeetingLlmBudgets.EXTRACTION_MAX_TOKENS);
            contextWindowGuard.assertTranscriptFitsBudget(normalized, chunkingConfig);

            List<TranscriptChunk> chunks = chunker.chunk(normalized, chunkingConfig);
            String corpus = groundingCorpus(normalized);

            String meetingTitle = request.meetingOccurrenceId().toString();
            String language = request.language();
            int timeoutSeconds = request.timeoutSeconds();
            List<ExtractionBundle> perChunk = extractChunks(
                    chunks,
                    prompt,
                    descriptor,
                    corpus,
                    meetingTitle,
                    language,
                    timeoutSeconds,
                    request.parallelChunkLimit(),
                    metrics
            );

            ExtractionBundle merged = merger.merge(perChunk);
            merged = ProposalCuePostProcessor.productionDefaults().process(merged, normalized);
            merged = CrossTypeMeetingItemScrubber.productionDefaults().scrub(merged);
            merged = new CrossTypeConsistencyAuditor().auditBundle(merged);
            Set<String> roster = new LinkedHashSet<>(ActionPostProcessingPipeline.participantsFromSegments(normalized));
            if (request.meetingParticipantNames() != null) {
                for (String name : request.meetingParticipantNames()) {
                    if (name != null && !name.isBlank()) {
                        roster.add(name.strip());
                    }
                }
            }
            ActionPostProcessingPipeline.Context actionCtx = new ActionPostProcessingPipeline.Context(
                    normalized,
                    roster,
                    ActionPostProcessingPipeline.parseMeetingStart(request.meetingStartedAtIso(), ActionPostProcessingPipeline.DEFAULT_ZONE),
                    ActionPostProcessingPipeline.DEFAULT_ZONE,
                    request.meetingOccurrenceId() == null ? null : request.meetingOccurrenceId().toString()
            );
            merged = ActionPostProcessingPipeline.productionDefaults().applyToBundle(merged, actionCtx);
            merged = new CrossTypeMeetingItemSubsumer().applyToBundle(merged);
            Set<String> allowed = normalized.stream()
                    .map(SegmentInput::segmentId)
                    .collect(Collectors.toCollection(HashSet::new));
            deterministicValidator.validate(merged, allowed, corpus);

            FinalNoteDraft deterministic = finalNoteAssembler.assemble(merged, language);
            MinutesFinalizationResult finalization =
                    new MinutesSynthesisAndAudit(
                            modelRuntime,
                            timeoutSeconds,
                            qualityMetrics,
                            finalizationPolicy
                    ).finalizeMinutes(
                            merged,
                            deterministic,
                            allowed,
                            meetingTitle,
                            language,
                            request.priorMeetingContext()
                    );
            metrics.recordFinalization(
                    finalization.mode(),
                    finalization.fallbackUsed(),
                    finalization.modelCalls(),
                    finalization.inputTokens(),
                    finalization.outputTokens(),
                    finalization.modelLatencyMs()
            );
            FinalNoteDraft note = finalization.draft();
            note = new ActionContextualEnricher().enrich(note, normalized);
            ExplicitActionCueRecoverer.Result recovered =
                    new ExplicitActionCueRecoverer().recover(note.actionItems(), normalized);
            note = withActions(note, recovered.actions());
            ActionPostProcessingPipeline.AppliedDraft applied =
                    ActionPostProcessingPipeline.productionDefaults().applyToDraftDetailed(note, actionCtx);
            for (int i = 0; i < recovered.recovered(); i++) {
                applied.stats().incrementExplicitActionCuesRecovered();
            }
            note = applied.draft();
            note = new CrossTypeMeetingItemSubsumer().applyToDraft(note);
            note = new CrossTypeConsistencyAuditor().audit(note, finalization.fallbackUsed());
            metrics.setActionPostProcessingStats(applied.stats().toArtifactMap(
                    request.meetingOccurrenceId() == null ? null : request.meetingOccurrenceId().toString()));
            note = FinalNoteConfidencePolicy.productionDefaults().apply(note);
            metrics.addDurationMs((System.nanoTime() - pipelineStarted) / 1_000_000L);
            return PipelineRunResult.succeeded(promptVersionId, modelVersion, note, metrics);
        } catch (PipelineException ex) {
            metrics.addDurationMs((System.nanoTime() - pipelineStarted) / 1_000_000L);
            return PipelineRunResult.failed(
                    promptVersionId,
                    modelVersion,
                    metrics,
                    ex.category(),
                    ex.getMessage(),
                    isPermanentRunFailure(ex.category())
            );
        } catch (ModelUnavailableException ex) {
            metrics.addDurationMs((System.nanoTime() - pipelineStarted) / 1_000_000L);
            return PipelineRunResult.failed(
                    promptVersionId,
                    modelVersion,
                    metrics,
                    FailureCategory.MODEL_UNAVAILABLE,
                    ex.getMessage(),
                    false
            );
        }
    }

    private static FinalNoteDraft withActions(FinalNoteDraft draft, List<ActionItemCandidate> actions) {
        return new FinalNoteDraft(
                draft.executiveSummary(),
                draft.decisions(),
                actions,
                draft.risks(),
                draft.openQuestions(),
                draft.commitments(),
                draft.topics(),
                draft.issues(),
                draft.proposals(),
                draft.importantFacts(),
                draft.qualityFlags(),
                draft.evidenceSegmentIds(),
                draft.confidence(),
                draft.requiresManualReview()
        );
    }

    /**
     * Job-level permanence. {@link FailureCategory#MODEL_UNAVAILABLE} is retriable by
     * the FAZ 13 executor (re-queue); in-chunk {@code INVALID_JSON} retries are exhausted
     * inside {@link #extractChunkWithRetry} so the run result is permanent.
     */
    private static boolean isPermanentRunFailure(FailureCategory category) {
        return category != FailureCategory.MODEL_UNAVAILABLE;
    }

    /**
     * Extract chunks with a merge barrier. Permanent per-chunk failures are isolated:
     * surviving chunks still merge. The run fails only when every chunk fails
     * (or {@link FailureCategory#MODEL_UNAVAILABLE} aborts the job).
     */
    private List<ExtractionBundle> extractChunks(
            List<TranscriptChunk> chunks,
            PublishedPrompt prompt,
            ModelDescriptor descriptor,
            String corpus,
            String meetingTitle,
            String language,
            int timeoutSeconds,
            int parallelChunkLimit,
            PipelineRunMetrics metrics
    ) {
        if (chunks.isEmpty()) {
            return List.of();
        }
        List<ChunkSignalSummary> summaries = precomputeSummaries(chunks, language);
        if (chunks.size() == 1 || parallelChunkLimit <= 1) {
            List<ExtractionBundle> sequential = new ArrayList<>();
            PipelineException lastPermanent = null;
            for (int i = 0; i < chunks.size(); i++) {
                metrics.incrementChunkCount();
                TranscriptChunk chunk = chunks.get(i);
                try {
                    sequential.add(extractChunkWithRetry(
                            prompt, descriptor, chunk,
                            previousSummary(summaries, i), nextSummary(summaries, i),
                            corpus, meetingTitle, language, timeoutSeconds, metrics));
                } catch (PipelineException ex) {
                    if (ex.category() == FailureCategory.MODEL_UNAVAILABLE) {
                        throw ex;
                    }
                    lastPermanent = ex;
                    metrics.incrementFailedChunkCount();
                    int chunkIndex = chunk.index();
                    LOG.warning(() -> "extraction.chunk.failed index=" + chunkIndex
                            + " category=" + ex.category() + " message=" + ex.getMessage());
                }
            }
            if (sequential.isEmpty()) {
                throw lastPermanent != null
                        ? lastPermanent
                        : new PipelineException(
                                FailureCategory.UNKNOWN,
                                PipelineStage.EXTRACT,
                                "All chunks failed without exception"
                        );
            }
            return sequential;
        }

        int workers = Math.min(parallelChunkLimit, chunks.size());
        ExecutorService pool = Executors.newFixedThreadPool(workers, r -> {
            Thread t = new Thread(r, "ai-chunk-extract");
            t.setDaemon(true);
            return t;
        });
        try {
            @SuppressWarnings("unchecked")
            CompletableFuture<ChunkAttempt>[] futures = new CompletableFuture[chunks.size()];
            for (int i = 0; i < chunks.size(); i++) {
                TranscriptChunk chunk = chunks.get(i);
                ChunkSignalSummary previous = previousSummary(summaries, i);
                ChunkSignalSummary next = nextSummary(summaries, i);
                String lang = language;
                futures[i] = CompletableFuture.supplyAsync(() -> {
                    metrics.incrementChunkCount();
                    try {
                        return ChunkAttempt.ok(extractChunkWithRetry(
                                prompt, descriptor, chunk, previous, next,
                                corpus, meetingTitle, lang, timeoutSeconds, metrics));
                    } catch (PipelineException ex) {
                        if (ex.category() == FailureCategory.MODEL_UNAVAILABLE) {
                            throw ex;
                        }
                        metrics.incrementFailedChunkCount();
                        LOG.warning(() -> "extraction.chunk.failed index=" + chunk.index()
                                + " category=" + ex.category() + " message=" + ex.getMessage());
                        return ChunkAttempt.failed(ex);
                    }
                }, pool);
            }
            CompletableFuture.allOf(futures).join();
            List<ExtractionBundle> ordered = new ArrayList<>();
            PipelineException lastPermanent = null;
            for (CompletableFuture<ChunkAttempt> future : futures) {
                ChunkAttempt attempt = future.join();
                if (attempt.bundle() != null) {
                    ordered.add(attempt.bundle());
                } else {
                    lastPermanent = attempt.failure();
                }
            }
            if (ordered.isEmpty()) {
                throw lastPermanent != null
                        ? lastPermanent
                        : new PipelineException(
                                FailureCategory.UNKNOWN,
                                PipelineStage.EXTRACT,
                                "All chunks failed without exception"
                        );
            }
            return ordered;
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof PipelineException pipelineException) {
                throw pipelineException;
            }
            if (cause instanceof ModelUnavailableException modelUnavailable) {
                throw modelUnavailable;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new PipelineException(
                    FailureCategory.UNKNOWN,
                    PipelineStage.EXTRACT,
                    cause.getMessage() == null ? "parallel chunk extraction failed" : cause.getMessage()
            );
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException interrupted) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private record ChunkAttempt(ExtractionBundle bundle, PipelineException failure) {
        static ChunkAttempt ok(ExtractionBundle bundle) {
            return new ChunkAttempt(bundle, null);
        }

        static ChunkAttempt failed(PipelineException failure) {
            return new ChunkAttempt(null, failure);
        }
    }

    private List<ChunkSignalSummary> precomputeSummaries(List<TranscriptChunk> chunks, String language) {
        List<ChunkSignalSummary> summaries = new ArrayList<>(chunks.size());
        ChunkSignalSummary previous = ChunkSignalSummary.empty();
        for (TranscriptChunk chunk : chunks) {
            ChunkContext ctx = ChunkContext.withNeighbors(
                    signalGateConfig, language, previous, null);
            ChunkSignalSummary summary = signalFeatureExtractor.extract(chunk, ctx).toSummary();
            summaries.add(summary);
            previous = summary;
        }
        return summaries;
    }

    private static ChunkSignalSummary previousSummary(List<ChunkSignalSummary> summaries, int index) {
        return index <= 0 ? ChunkSignalSummary.empty() : summaries.get(index - 1);
    }

    private static ChunkSignalSummary nextSummary(List<ChunkSignalSummary> summaries, int index) {
        return index + 1 >= summaries.size() ? null : summaries.get(index + 1);
    }

    private ExtractionBundle extractChunkWithRetry(
            PublishedPrompt prompt,
            ModelDescriptor descriptor,
            TranscriptChunk chunk,
            ChunkSignalSummary previous,
            ChunkSignalSummary next,
            String fullCorpus,
            String meetingTitle,
            String language,
            int timeoutSeconds,
            PipelineRunMetrics metrics
    ) {
        ChunkContext context = ChunkContext.withNeighbors(
                signalGateConfig, language, previous, next);
        ChunkExtractionResult gated = chunkExtractionService.extract(chunk, context, c -> {
            try {
                return inferChunkOnce(
                        prompt, descriptor, c, fullCorpus, meetingTitle, language,
                        timeoutSeconds, metrics, MeetingLlmBudgets.EXTRACTION_MAX_TOKENS);
            } catch (PipelineException first) {
                if (first.category() == FailureCategory.MODEL_UNAVAILABLE) {
                    return retryModelUnavailableOnce(
                            prompt, descriptor, c, fullCorpus, meetingTitle, language,
                            timeoutSeconds, metrics, first);
                }
                if (first.category() != FailureCategory.INVALID_JSON) {
                    throw first;
                }

                // Attempt 2: reduced context + lower output budget (not a blind repeat).
                metrics.incrementInvalidJsonRetry();
                LOG.info("INVALID_JSON_RETRY_REDUCED_CONTEXT chunk=" + c.index());
                TranscriptChunk reduced = reduceChunkContext(c);
                try {
                    return inferChunkOnce(
                            prompt, descriptor, reduced, fullCorpus, meetingTitle, language,
                            timeoutSeconds, metrics, MeetingLlmBudgets.EXTRACTION_MAX_TOKENS / 2);
                } catch (PipelineException second) {
                    if (second.category() != FailureCategory.INVALID_JSON) {
                        throw second;
                    }
                }

                // Attempt 3: split into child chunks when possible.
                if (c.segments().size() >= 2) {
                    metrics.incrementInvalidJsonRetry();
                    LOG.info("INVALID_JSON_RETRY_SPLIT_CHUNK chunk=" + c.index());
                    return inferSplitChunk(
                            prompt, descriptor, c, fullCorpus, meetingTitle, language,
                            timeoutSeconds, metrics);
                }
                throw first;
            } catch (ModelUnavailableException ex) {
                PipelineException wrapped = new PipelineException(
                        FailureCategory.MODEL_UNAVAILABLE,
                        PipelineStage.EXTRACT,
                        ex.getMessage()
                );
                return retryModelUnavailableOnce(
                        prompt, descriptor, c, fullCorpus, meetingTitle, language,
                        timeoutSeconds, metrics, wrapped);
            }
        });
        return gated.bundle();
    }

    private ExtractionBundle retryModelUnavailableOnce(
            PublishedPrompt prompt,
            ModelDescriptor descriptor,
            TranscriptChunk chunk,
            String fullCorpus,
            String meetingTitle,
            String language,
            int timeoutSeconds,
            PipelineRunMetrics metrics,
            PipelineException first
    ) {
        RetryDecision decision = retryClassifier.classify(first, null);
        if (decision == RetryDecision.PERMANENT_FAILURE) {
            throw first;
        }
        try {
            return inferChunkOnce(
                    prompt, descriptor, chunk, fullCorpus, meetingTitle, language,
                    timeoutSeconds, metrics, MeetingLlmBudgets.EXTRACTION_MAX_TOKENS);
        } catch (PipelineException second) {
            if (retryClassifier.classify(second, first.fingerprint()) == RetryDecision.PERMANENT_FAILURE) {
                throw second;
            }
            throw second;
        } catch (ModelUnavailableException ex) {
            throw new PipelineException(
                    FailureCategory.MODEL_UNAVAILABLE,
                    PipelineStage.EXTRACT,
                    ex.getMessage()
            );
        }
    }

    private static TranscriptChunk reduceChunkContext(TranscriptChunk chunk) {
        List<SegmentInput> segments = chunk.segments();
        if (segments.size() <= 1) {
            return chunk;
        }
        int keep = Math.max(1, segments.size() / 2);
        List<SegmentInput> reduced = segments.subList(0, keep);
        int tokens = Math.max(1, chunk.estimatedTokens() * keep / segments.size());
        return new TranscriptChunk(chunk.index(), reduced, tokens);
    }

    private ExtractionBundle inferSplitChunk(
            PublishedPrompt prompt,
            ModelDescriptor descriptor,
            TranscriptChunk chunk,
            String fullCorpus,
            String meetingTitle,
            String language,
            int timeoutSeconds,
            PipelineRunMetrics metrics
    ) {
        List<SegmentInput> segments = chunk.segments();
        int mid = segments.size() / 2;
        TranscriptChunk left = new TranscriptChunk(
                chunk.index(),
                segments.subList(0, mid),
                Math.max(1, chunk.estimatedTokens() / 2)
        );
        TranscriptChunk right = new TranscriptChunk(
                chunk.index(),
                segments.subList(mid, segments.size()),
                Math.max(1, chunk.estimatedTokens() - left.estimatedTokens())
        );
        ExtractionBundle leftBundle = inferChunkOnce(
                prompt, descriptor, left, fullCorpus, meetingTitle, language,
                timeoutSeconds, metrics, MeetingLlmBudgets.EXTRACTION_MAX_TOKENS);
        ExtractionBundle rightBundle = inferChunkOnce(
                prompt, descriptor, right, fullCorpus, meetingTitle, language,
                timeoutSeconds, metrics, MeetingLlmBudgets.EXTRACTION_MAX_TOKENS);
        return merger.merge(List.of(leftBundle, rightBundle));
    }

    private ExtractionBundle inferChunkOnce(
            PublishedPrompt prompt,
            ModelDescriptor descriptor,
            TranscriptChunk chunk,
            String fullCorpus,
            String meetingTitle,
            String language,
            int timeoutSeconds,
            PipelineRunMetrics metrics,
            int maxOutputTokens
    ) {
        List<String> evidenceIds = chunk.segmentIds();
        String userPrompt = renderPrompt(
                prompt.template(),
                chunk,
                evidenceIds,
                meetingTitle,
                language,
                meetingDateForPrompt
        );
        String systemPrompt = ExtractionPromptRules.systemRulesFor(language);

        contextWindowGuard.assertFits(
                systemPrompt + "\n" + userPrompt,
                descriptor.contextWindowTokens(),
                maxOutputTokens
        );

        InferenceRequest inferenceRequest = new InferenceRequest(
                InferenceTaskType.CHUNK_EXTRACTION.name(),
                prompt.promptVersionId(),
                prompt.outputSchemaId(),
                systemPrompt,
                userPrompt,
                evidenceIds,
                maxOutputTokens,
                timeoutSeconds
        );

        InferenceResponse response = modelRuntime.infer(inferenceRequest);
        metrics.recordModelUsage(
                PipelineStage.EXTRACT.name(),
                1,
                response.inputTokens(),
                response.outputTokens(),
                response.latencyMs()
        );

        promptInjectionGuard.assertClean(response.rawText());

        JsonNode node = parseExtractionJson(response.rawText(), metrics);
        ExtractionBundle bundle = bundleMapper.fromJson(node);

        // Scrub / ground evidence BEFORE hard validation so unknown IDs soft-drop items.
        Set<String> allowed = new HashSet<>(evidenceIds);
        EvidenceReferenceScrubber.Outcome scrub = evidenceScrubber.scrub(bundle, allowed);
        metrics.addEvidenceScrub(scrub.droppedRefs(), scrub.correctedRefs(), scrub.droppedItems());
        if (scrub.droppedRefs() > 0 || scrub.correctedRefs() > 0 || scrub.droppedItems() > 0) {
            LOG.info(() -> "evidence.scrub chunk=" + chunk.index()
                    + " droppedRefs=" + scrub.droppedRefs()
                    + " correctedRefs=" + scrub.correctedRefs()
                    + " droppedItems=" + scrub.droppedItems());
        }

        String groundingCorpus = chunk.joinedContent() + "\n" + fullCorpus;
        deterministicValidator.validate(scrub.bundle(), allowed, groundingCorpus);
        return ProposalCuePostProcessor.productionDefaults().process(scrub.bundle(), chunk.segments());
    }

    private JsonNode parseExtractionJson(String raw, PipelineRunMetrics metrics) {
        String json = raw;
        try {
            if (jsonRepair.needsRepair(json)) {
                json = jsonRepair.repairOrThrow(json);
                metrics.incrementRepairCount();
            } else {
                json = json.trim();
            }
            return schemaValidator.parseAndValidate(json);
        } catch (PipelineException ex) {
            if (ex.category() != FailureCategory.INVALID_JSON
                    && ex.category() != FailureCategory.SCHEMA_VIOLATION) {
                throw ex;
            }
            Optional<String> recovered = partialJsonRecovery.recover(raw);
            if (recovered.isEmpty()) {
                if (ex.category() == FailureCategory.INVALID_JSON) {
                    throw ex;
                }
                throw new PipelineException(
                        FailureCategory.INVALID_JSON,
                        PipelineStage.EXTRACT,
                        "Unable to repair model JSON"
                );
            }
            metrics.incrementPartialJsonRecovery();
            metrics.incrementRepairCount();
            return schemaValidator.parseAndValidate(recovered.get());
        }
    }

    private static String renderPrompt(
            String template,
            TranscriptChunk chunk,
            List<String> evidenceIds,
            String meetingTitle,
            String language
    ) {
        return renderPrompt(template, chunk, evidenceIds, meetingTitle, language, "");
    }

    private static String renderPrompt(
            String template,
            TranscriptChunk chunk,
            List<String> evidenceIds,
            String meetingTitle,
            String language,
            String meetingDate
    ) {
        return ExtractionPromptRules.applyLanguage(template, language)
                .replace("{{meetingTitle}}", meetingTitle == null ? "" : meetingTitle)
                .replace("{{meetingDate}}", meetingDate == null ? "" : meetingDate)
                .replace("{{participants}}", "")
                .replace("{{evidenceSegmentIds}}", String.join(",", evidenceIds))
                .replace("{{chunk}}", formatChunk(chunk));
    }

    private static String formatChunk(TranscriptChunk chunk) {
        StringBuilder sb = new StringBuilder();
        for (SegmentInput segment : chunk.segments()) {
            sb.append('[').append(segment.segmentId()).append("] ");
            segment.speakerDisplayNameOptional().ifPresent(name -> sb.append(name).append(": "));
            sb.append(segment.content()).append('\n');
        }
        return sb.toString();
    }

    private static String groundingCorpus(List<SegmentInput> segments) {
        StringBuilder sb = new StringBuilder();
        for (SegmentInput segment : segments) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            segment.speakerDisplayNameOptional().ifPresent(name -> sb.append(name).append(' '));
            sb.append(segment.content());
        }
        return sb.toString();
    }
}
