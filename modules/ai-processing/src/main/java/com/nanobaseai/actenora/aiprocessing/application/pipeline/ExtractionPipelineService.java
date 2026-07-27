package com.nanobaseai.actenora.aiprocessing.application.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ChunkingConfig;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ContextWindowGuard;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DeterministicExtractionValidator;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionMerger;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FailureCategory;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteAssembler;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
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
import com.nanobaseai.actenora.aiprocessing.domain.prompt.ExtractionPromptRules;
import com.nanobaseai.actenora.aiprocessing.domain.prompt.PublishedPrompt;
import com.nanobaseai.actenora.aiprocessing.domain.routing.InferenceTaskType;
import com.nanobaseai.actenora.aiprocessing.infrastructure.json.ExtractionBundleMapper;
import com.nanobaseai.actenora.aiprocessing.infrastructure.json.ExtractionJsonSchemaValidator;
import com.nanobaseai.actenora.aiprocessing.infrastructure.json.LimitedJsonRepair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Production extraction pipeline:
 * normalize → chunk → extract → merge → deterministic validate → final note.
 * Model identity stays behind {@link ModelRuntimePort}; no cloud fallback.
 */
public final class ExtractionPipelineService {

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
    }

    public static ExtractionPipelineService create(
            PromptRegistryPort promptRegistry,
            ModelRuntimePort modelRuntime
    ) {
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
                new PromptInjectionGuard()
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
            ChunkingConfig chunkingConfig = ChunkingConfig.productionDefaults(descriptor.contextWindowTokens())
                    .withMaxOutput(descriptor.maxOutputTokens());
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
            Set<String> allowed = normalized.stream()
                    .map(SegmentInput::segmentId)
                    .collect(Collectors.toCollection(HashSet::new));
            deterministicValidator.validate(merged, allowed, corpus);

            FinalNoteDraft deterministic = finalNoteAssembler.assemble(merged, language);
            FinalNoteDraft note = new MinutesSynthesisAndAudit(modelRuntime, timeoutSeconds)
                    .synthesizeAndAudit(merged, deterministic, allowed, meetingTitle, language);
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

    /**
     * Job-level permanence. {@link FailureCategory#MODEL_UNAVAILABLE} is retriable by
     * the FAZ 13 executor (re-queue); in-chunk {@code INVALID_JSON} retries are exhausted
     * inside {@link #extractChunkWithRetry} so the run result is permanent.
     */
    private static boolean isPermanentRunFailure(FailureCategory category) {
        return category != FailureCategory.MODEL_UNAVAILABLE;
    }

    /**
     * Extract chunks with a merge barrier. Parallelism is bounded by {@code parallelChunkLimit}
     * and further constrained by provider extraction semaphores. Merge/final stay single-threaded
     * after this method returns.
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
        if (chunks.size() == 1 || parallelChunkLimit <= 1) {
            List<ExtractionBundle> sequential = new ArrayList<>(chunks.size());
            for (TranscriptChunk chunk : chunks) {
                metrics.incrementChunkCount();
                sequential.add(extractChunkWithRetry(
                        prompt, descriptor, chunk, corpus, meetingTitle, language, timeoutSeconds, metrics));
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
            CompletableFuture<ExtractionBundle>[] futures = new CompletableFuture[chunks.size()];
            for (int i = 0; i < chunks.size(); i++) {
                TranscriptChunk chunk = chunks.get(i);
                futures[i] = CompletableFuture.supplyAsync(() -> {
                    metrics.incrementChunkCount();
                    return extractChunkWithRetry(
                            prompt, descriptor, chunk, corpus, meetingTitle, language, timeoutSeconds, metrics);
                }, pool);
            }
            CompletableFuture.allOf(futures).join();
            List<ExtractionBundle> ordered = new ArrayList<>(chunks.size());
            for (CompletableFuture<ExtractionBundle> future : futures) {
                ordered.add(future.join());
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

    private ExtractionBundle extractChunkWithRetry(
            PublishedPrompt prompt,
            ModelDescriptor descriptor,
            TranscriptChunk chunk,
            String fullCorpus,
            String meetingTitle,
            String language,
            int timeoutSeconds,
            PipelineRunMetrics metrics
    ) {
        String previousFingerprint = null;
        PipelineException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return extractOnce(
                        prompt, descriptor, chunk, fullCorpus, meetingTitle, language, timeoutSeconds, metrics);
            } catch (PipelineException ex) {
                last = ex;
                RetryDecision decision = retryClassifier.classify(ex, previousFingerprint);
                if (decision == RetryDecision.PERMANENT_FAILURE) {
                    throw ex;
                }
                previousFingerprint = ex.fingerprint();
            } catch (ModelUnavailableException ex) {
                PipelineException wrapped = new PipelineException(
                        FailureCategory.MODEL_UNAVAILABLE,
                        PipelineStage.EXTRACT,
                        ex.getMessage()
                );
                last = wrapped;
                RetryDecision decision = retryClassifier.classify(wrapped, previousFingerprint);
                if (decision == RetryDecision.PERMANENT_FAILURE) {
                    throw wrapped;
                }
                previousFingerprint = wrapped.fingerprint();
            }
        }
        throw last != null
                ? last
                : new PipelineException(
                        FailureCategory.UNKNOWN,
                        PipelineStage.EXTRACT,
                        "Extraction failed without exception"
                );
    }

    private ExtractionBundle extractOnce(
            PublishedPrompt prompt,
            ModelDescriptor descriptor,
            TranscriptChunk chunk,
            String fullCorpus,
            String meetingTitle,
            String language,
            int timeoutSeconds,
            PipelineRunMetrics metrics
    ) {
        List<String> evidenceIds = chunk.segmentIds();
        String userPrompt = renderPrompt(prompt.template(), chunk, evidenceIds, meetingTitle, language);
        String systemPrompt = ExtractionPromptRules.systemRulesFor(language);

        contextWindowGuard.assertFits(
                systemPrompt + "\n" + userPrompt,
                descriptor.contextWindowTokens(),
                descriptor.maxOutputTokens()
        );

        InferenceRequest inferenceRequest = new InferenceRequest(
                InferenceTaskType.CHUNK_EXTRACTION.name(),
                prompt.promptVersionId(),
                prompt.outputSchemaId(),
                systemPrompt,
                userPrompt,
                evidenceIds,
                descriptor.maxOutputTokens(),
                timeoutSeconds
        );

        InferenceResponse response = modelRuntime.infer(inferenceRequest);
        metrics.addInputTokens(response.inputTokens());
        metrics.addOutputTokens(response.outputTokens());
        metrics.addDurationMs(response.latencyMs());

        promptInjectionGuard.assertClean(response.rawText());

        String json = response.rawText();
        if (jsonRepair.needsRepair(json)) {
            json = jsonRepair.repairOrThrow(json);
            metrics.incrementRepairCount();
        } else {
            json = json.trim();
        }

        JsonNode node = schemaValidator.parseAndValidate(json);
        ExtractionBundle bundle = bundleMapper.fromJson(node);

        Set<String> allowed = new HashSet<>(evidenceIds);
        // Deterministic checks use chunk corpus for owner/date grounding within chunk text,
        // plus full corpus so owners spoken earlier still validate.
        String groundingCorpus = chunk.joinedContent() + "\n" + fullCorpus;
        deterministicValidator.validate(bundle, allowed, groundingCorpus);
        return bundle;
    }

    private static String renderPrompt(
            String template,
            TranscriptChunk chunk,
            List<String> evidenceIds,
            String meetingTitle,
            String language
    ) {
        return ExtractionPromptRules.applyLanguage(template, language)
                .replace("{{meetingTitle}}", meetingTitle == null ? "" : meetingTitle)
                .replace("{{meetingDate}}", "")
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
