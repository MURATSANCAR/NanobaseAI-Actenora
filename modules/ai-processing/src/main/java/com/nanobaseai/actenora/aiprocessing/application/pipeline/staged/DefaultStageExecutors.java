package com.nanobaseai.actenora.aiprocessing.application.pipeline.staged;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.InferenceRequest;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.PriorMeetingContext;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.InferenceResponse;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.MinutesSynthesisAndAudit;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.MinutesFinalizationPolicy;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.MinutesFinalizationResult;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelDescriptor;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelRuntimePort;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.PriorMeetingContextPort;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.PromptRegistryPort;
import com.nanobaseai.actenora.aiprocessing.application.port.ApprovedKnowledgeIndexPort;
import com.nanobaseai.actenora.aiprocessing.application.port.MeetingNoteHandoffPort;
import com.nanobaseai.actenora.aiprocessing.application.port.MeetingOccurrenceClockPort;
import com.nanobaseai.actenora.aiprocessing.application.port.PipelineQualityMetricsPort;
import com.nanobaseai.actenora.aiprocessing.application.port.ProcessingArtifactRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.TranscriptSegmentSourcePort;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingArtifact;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingStage;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.extraction.MeasuredObservationFactSeeder;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.extraction.ProposalCuePostProcessor;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.filter.CrossTypeMeetingItemScrubber;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency.CrossTypeMeetingItemSubsumer;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.action.ActionPostProcessingPipeline;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.action.ExplicitActionCueRecoverer;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.action.ActionPostProcessingStats;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency.ActionContextualEnricher;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency.CrossTypeConsistencyAuditor;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.note.FinalNoteConfidencePolicy;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ChunkingConfig;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ContextWindowGuard;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.MeetingLlmBudgets;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DeterministicExtractionValidator;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionMerger;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteAssembler;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Production stage executors for the staged pipeline. Reuses FAZ-14 building blocks.
 */
public final class DefaultStageExecutors {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DefaultStageExecutors() {
    }

    public static Map<ProcessingStage, StageExecutor> createAll(
            PromptRegistryPort prompts,
            ModelRuntimePort modelRuntime,
            TranscriptSegmentSourcePort segments,
            ProcessingArtifactRepository artifacts,
            PriorMeetingContextPort priorContext
    ) {
        return createAll(
                prompts,
                modelRuntime,
                segments,
                artifacts,
                priorContext,
                MeetingNoteHandoffPort.noop(),
                ApprovedKnowledgeIndexPort.noop()
        );
    }

    public static Map<ProcessingStage, StageExecutor> createAll(
            PromptRegistryPort prompts,
            ModelRuntimePort modelRuntime,
            TranscriptSegmentSourcePort segments,
            ProcessingArtifactRepository artifacts,
            PriorMeetingContextPort priorContext,
            MeetingNoteHandoffPort noteHandoff,
            ApprovedKnowledgeIndexPort knowledgeIndex
    ) {
        return createAll(
                prompts, modelRuntime, segments, artifacts, priorContext, noteHandoff, knowledgeIndex,
                ChunkExtractionService.createDefault(),
                PipelineQualityMetricsPort.noop()
        );
    }

    public static Map<ProcessingStage, StageExecutor> createAll(
            PromptRegistryPort prompts,
            ModelRuntimePort modelRuntime,
            TranscriptSegmentSourcePort segments,
            ProcessingArtifactRepository artifacts,
            PriorMeetingContextPort priorContext,
            MeetingNoteHandoffPort noteHandoff,
            ApprovedKnowledgeIndexPort knowledgeIndex,
            ChunkExtractionService chunkExtraction
    ) {
        return createAll(
                prompts, modelRuntime, segments, artifacts, priorContext, noteHandoff, knowledgeIndex,
                chunkExtraction,
                PipelineQualityMetricsPort.noop()
        );
    }

    public static Map<ProcessingStage, StageExecutor> createAll(
            PromptRegistryPort prompts,
            ModelRuntimePort modelRuntime,
            TranscriptSegmentSourcePort segments,
            ProcessingArtifactRepository artifacts,
            PriorMeetingContextPort priorContext,
            MeetingNoteHandoffPort noteHandoff,
            ApprovedKnowledgeIndexPort knowledgeIndex,
            ChunkExtractionService chunkExtraction,
            PipelineQualityMetricsPort qualityMetrics
    ) {
        return createAll(
                prompts, modelRuntime, segments, artifacts, priorContext, noteHandoff, knowledgeIndex,
                chunkExtraction, qualityMetrics, MeetingOccurrenceClockPort.unsupported()
        );
    }

    public static Map<ProcessingStage, StageExecutor> createAll(
            PromptRegistryPort prompts,
            ModelRuntimePort modelRuntime,
            TranscriptSegmentSourcePort segments,
            ProcessingArtifactRepository artifacts,
            PriorMeetingContextPort priorContext,
            MeetingNoteHandoffPort noteHandoff,
            ApprovedKnowledgeIndexPort knowledgeIndex,
            ChunkExtractionService chunkExtraction,
            PipelineQualityMetricsPort qualityMetrics,
            MeetingOccurrenceClockPort meetingClock
    ) {
        return createAll(
                prompts,
                modelRuntime,
                segments,
                artifacts,
                priorContext,
                noteHandoff,
                knowledgeIndex,
                chunkExtraction,
                qualityMetrics,
                meetingClock,
                MinutesFinalizationPolicy.compatibility()
        );
    }

    public static Map<ProcessingStage, StageExecutor> createAll(
            PromptRegistryPort prompts,
            ModelRuntimePort modelRuntime,
            TranscriptSegmentSourcePort segments,
            ProcessingArtifactRepository artifacts,
            PriorMeetingContextPort priorContext,
            MeetingNoteHandoffPort noteHandoff,
            ApprovedKnowledgeIndexPort knowledgeIndex,
            ChunkExtractionService chunkExtraction,
            PipelineQualityMetricsPort qualityMetrics,
            MeetingOccurrenceClockPort meetingClock,
            MinutesFinalizationPolicy finalizationPolicy
    ) {
        SegmentNormalizer normalizer = new SegmentNormalizer();
        TranscriptChunker chunker = new TranscriptChunker();
        ContextWindowGuard guard = new ContextWindowGuard();
        LimitedJsonRepair repair = new LimitedJsonRepair();
        ExtractionJsonSchemaValidator schema = new ExtractionJsonSchemaValidator();
        ExtractionBundleMapper bundleMapper = new ExtractionBundleMapper();
        DeterministicExtractionValidator validator = new DeterministicExtractionValidator();
        ExtractionMerger merger = new ExtractionMerger();
        FinalNoteAssembler noteAssembler = new FinalNoteAssembler();
        ChunkExtractionService extraction = chunkExtraction == null
                ? ChunkExtractionService.createDefault()
                : chunkExtraction;
        PipelineQualityMetricsPort metrics = qualityMetrics == null
                ? PipelineQualityMetricsPort.noop()
                : qualityMetrics;
        MeetingOccurrenceClockPort clock = meetingClock == null
                ? MeetingOccurrenceClockPort.unsupported()
                : meetingClock;

        return Map.of(
                ProcessingStage.NORMALIZE, new NormalizeExecutor(segments, normalizer, artifacts),
                ProcessingStage.TRIAGE, new TriageExecutor(prompts, modelRuntime, segments, normalizer),
                ProcessingStage.CHUNK, new ChunkPlanExecutor(segments, normalizer, chunker, guard, modelRuntime, artifacts),
                ProcessingStage.EXTRACT, new ExtractChunkExecutor(
                        prompts, modelRuntime, segments, normalizer, chunker, guard, repair, schema, bundleMapper,
                        validator, artifacts, extraction),
                ProcessingStage.MERGE, new MergeExecutor(
                        modelRuntime, prompts, artifacts, merger, repair, schema, bundleMapper,
                        segments, normalizer),
                ProcessingStage.VALIDATE, new ValidateExecutor(artifacts, validator, segments, normalizer),
                ProcessingStage.MINUTES, new MinutesExecutor(
                        modelRuntime,
                        artifacts,
                        noteAssembler,
                        segments,
                        normalizer,
                        priorContext == null ? PriorMeetingContextPort.noop() : priorContext,
                        noteHandoff == null ? MeetingNoteHandoffPort.noop() : noteHandoff,
                        metrics,
                        clock,
                        finalizationPolicy == null
                                ? MinutesFinalizationPolicy.compatibility()
                                : finalizationPolicy
                ),
                ProcessingStage.EMBEDDING, new EmbeddingExecutor(
                        knowledgeIndex == null ? ApprovedKnowledgeIndexPort.noop() : knowledgeIndex
                )
        );
    }

    private static List<SegmentInput> loadSegments(TranscriptSegmentSourcePort segments, AiJob job) {
        return segments.segmentsFor(TenantId.of(job.tenantId()), job.transcriptId());
    }

    private static String groundingCorpus(List<SegmentInput> normalized) {
        StringBuilder sb = new StringBuilder();
        for (SegmentInput segment : normalized) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            segment.speakerDisplayNameOptional().ifPresent(name -> sb.append(name).append(' '));
            sb.append(segment.content());
        }
        return sb.toString();
    }

    private static Optional<String> latestArtifact(
            ProcessingArtifactRepository artifacts,
            AiJob job,
            String type
    ) {
        return artifacts.findLatestByMeetingAndType(job.tenantId(), job.meetingOccurrenceId(), type)
                .flatMap(ProcessingArtifact::payloadJson);
    }

    static final class NormalizeExecutor implements StageExecutor {
        private final TranscriptSegmentSourcePort segments;
        private final SegmentNormalizer normalizer;
        private final ProcessingArtifactRepository artifacts;

        NormalizeExecutor(
                TranscriptSegmentSourcePort segments,
                SegmentNormalizer normalizer,
                ProcessingArtifactRepository artifacts
        ) {
            this.segments = segments;
            this.normalizer = normalizer;
            this.artifacts = artifacts;
        }

        @Override
        public ProcessingStage stage() {
            return ProcessingStage.NORMALIZE;
        }

        @Override
        public StageExecutionResult execute(AiJob job, Instant now) {
            long t0 = System.nanoTime();
            List<SegmentInput> normalized = normalizer.normalize(loadSegments(segments, job));
            ObjectNode node = MAPPER.createObjectNode();
            node.put("segmentCount", normalized.size());
            node.put("artifact", "normalized-segments");
            String json = node.toString();
            return StageExecutionResult.success(
                    job, "normalized", json, 0, 0, (System.nanoTime() - t0) / 1_000_000L, now);
        }
    }

    static final class TriageExecutor implements StageExecutor {
        private final PromptRegistryPort prompts;
        private final ModelRuntimePort modelRuntime;
        private final TranscriptSegmentSourcePort segments;
        private final SegmentNormalizer normalizer;

        TriageExecutor(
                PromptRegistryPort prompts,
                ModelRuntimePort modelRuntime,
                TranscriptSegmentSourcePort segments,
                SegmentNormalizer normalizer
        ) {
            this.prompts = prompts;
            this.modelRuntime = modelRuntime;
            this.segments = segments;
            this.normalizer = normalizer;
        }

        @Override
        public ProcessingStage stage() {
            return ProcessingStage.TRIAGE;
        }

        @Override
        public StageExecutionResult execute(AiJob job, Instant now) {
            long t0 = System.nanoTime();
            List<SegmentInput> normalized = normalizer.normalize(loadSegments(segments, job));
            String sample = groundingCorpus(normalized);
            if (sample.length() > 6_000) {
                sample = sample.substring(0, 6_000);
            }
            String system = ExtractionPromptRules.systemRulesFor(job.language())
                    + "\n\nTASK: Classify whether this meeting needs full decision/action extraction.";
            String user = """
                    Respond with JSON only:
                    {"containsDecisions":boolean,"containsActions":boolean,"containsRisks":boolean,"meetingType":"INFORMATIONAL"|"DECISION"|"MIXED"}
                    Transcript sample:
                    """ + sample;
            try {
                InferenceResponse response = modelRuntime.infer(new InferenceRequest(
                        InferenceTaskType.MEETING_TRIAGE.name(),
                        job.promptVersion(),
                        job.schemaVersion(),
                        system,
                        user,
                        List.of(),
                        MeetingLlmBudgets.TRIAGE_MAX_TOKENS,
                        120
                ));
                String json = sanitizeTriageJson(response.rawText());
                boolean early = isInformationalEarlyExit(json);
                long latency = (System.nanoTime() - t0) / 1_000_000L;
                int inTok = clampTokens(response.inputTokens());
                int outTok = clampTokens(response.outputTokens());
                if (early) {
                    return StageExecutionResult.earlyExit(
                            job, json, inTok, outTok, latency, now);
                }
                return StageExecutionResult.success(
                        job, "triage", json, inTok, outTok, latency, now);
            } catch (RuntimeException ex) {
                // Fail open to full path on triage errors.
                String fallback = triageFallbackJson();
                return StageExecutionResult.success(
                        job, "triage", fallback, 0, 0, (System.nanoTime() - t0) / 1_000_000L, now);
            }
        }

        private static String sanitizeTriageJson(String raw) {
            if (raw == null || raw.isBlank()) {
                return triageFallbackJson();
            }
            String trimmed = raw.trim();
            if (trimmed.startsWith("```")) {
                trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
                int close = trimmed.lastIndexOf("```");
                if (close >= 0) {
                    trimmed = trimmed.substring(0, close).trim();
                }
            }
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start, end + 1);
            }
            try {
                JsonNode node = MAPPER.readTree(trimmed);
                if (!node.isObject()) {
                    return triageFallbackJson();
                }
                // Keep only the classifier fields — models often dump full extraction JSON.
                ObjectNode out = MAPPER.createObjectNode();
                out.put("containsDecisions", boolOrTrue(node, "containsDecisions"));
                out.put("containsActions", boolOrTrue(node, "containsActions"));
                out.put("containsRisks", boolOrTrue(node, "containsRisks"));
                String meetingType = node.path("meetingType").asText("MIXED");
                if (meetingType.isBlank()) {
                    meetingType = "MIXED";
                }
                out.put("meetingType", meetingType);
                return out.toString();
            } catch (Exception ex) {
                return triageFallbackJson();
            }
        }

        private static boolean boolOrTrue(JsonNode node, String field) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull() || !value.isBoolean()) {
                return true;
            }
            return value.asBoolean();
        }

        private static String triageFallbackJson() {
            return """
                    {"containsDecisions":true,"containsActions":true,"containsRisks":true,"meetingType":"MIXED","fallback":true}
                    """.trim();
        }

        private static boolean isInformationalEarlyExit(String json) {
            String lower = json.toLowerCase();
            boolean informational = lower.contains("\"meetingtype\"") && lower.contains("informational");
            boolean noDecisions = lower.contains("\"containsdecisions\":false")
                    || lower.contains("\"containsdecisions\": false");
            boolean noActions = lower.contains("\"containsactions\":false")
                    || lower.contains("\"containsactions\": false");
            boolean noRisks = lower.contains("\"containsrisks\":false")
                    || lower.contains("\"containsrisks\": false");
            return informational && noDecisions && noActions && noRisks;
        }
    }

    static final class ChunkPlanExecutor implements StageExecutor {
        private final TranscriptSegmentSourcePort segments;
        private final SegmentNormalizer normalizer;
        private final TranscriptChunker chunker;
        private final ContextWindowGuard guard;
        private final ModelRuntimePort modelRuntime;
        private final ProcessingArtifactRepository artifacts;

        ChunkPlanExecutor(
                TranscriptSegmentSourcePort segments,
                SegmentNormalizer normalizer,
                TranscriptChunker chunker,
                ContextWindowGuard guard,
                ModelRuntimePort modelRuntime,
                ProcessingArtifactRepository artifacts
        ) {
            this.segments = segments;
            this.normalizer = normalizer;
            this.chunker = chunker;
            this.guard = guard;
            this.modelRuntime = modelRuntime;
            this.artifacts = artifacts;
        }

        @Override
        public ProcessingStage stage() {
            return ProcessingStage.CHUNK;
        }

        @Override
        public StageExecutionResult execute(AiJob job, Instant now) {
            long t0 = System.nanoTime();
            List<SegmentInput> normalized = normalizer.normalize(loadSegments(segments, job));
            ModelDescriptor descriptor = modelRuntime.descriptor();
            ChunkingConfig config = ChunkingConfig.productionDefaults(descriptor.contextWindowTokens())
                    .withMaxOutput(MeetingLlmBudgets.EXTRACTION_MAX_TOKENS);
            guard.assertTranscriptFitsBudget(normalized, config);
            List<TranscriptChunk> chunks = chunker.chunk(normalized, config);
            ObjectNode node = MAPPER.createObjectNode();
            node.put("chunkCount", chunks.size());
            node.put("targetTokens", config.effectiveTargetTokens());
            String json = node.toString();
            artifacts.save(ProcessingArtifact.inlineJson(
                    job.tenantId(), job.id(), job.meetingOccurrenceId(), "chunk-plan", json, now));
            return StageExecutionResult.success(
                    job, "chunk-plan", json, 0, 0, (System.nanoTime() - t0) / 1_000_000L, now);
        }
    }

    static final class ExtractChunkExecutor implements StageExecutor {
        private final PromptRegistryPort prompts;
        private final ModelRuntimePort modelRuntime;
        private final TranscriptSegmentSourcePort segments;
        private final SegmentNormalizer normalizer;
        private final TranscriptChunker chunker;
        private final ContextWindowGuard guard;
        private final LimitedJsonRepair repair;
        private final ExtractionJsonSchemaValidator schema;
        private final ExtractionBundleMapper bundleMapper;
        private final DeterministicExtractionValidator validator;
        private final ProcessingArtifactRepository artifacts;
        private final ChunkExtractionService chunkExtraction;
        private final ChunkSignalFeatureExtractor signalFeatures;
        private final SignalGateConfig signalGateConfig;

        ExtractChunkExecutor(
                PromptRegistryPort prompts,
                ModelRuntimePort modelRuntime,
                TranscriptSegmentSourcePort segments,
                SegmentNormalizer normalizer,
                TranscriptChunker chunker,
                ContextWindowGuard guard,
                LimitedJsonRepair repair,
                ExtractionJsonSchemaValidator schema,
                ExtractionBundleMapper bundleMapper,
                DeterministicExtractionValidator validator,
                ProcessingArtifactRepository artifacts,
                ChunkExtractionService chunkExtraction
        ) {
            this.prompts = prompts;
            this.modelRuntime = modelRuntime;
            this.segments = segments;
            this.normalizer = normalizer;
            this.chunker = chunker;
            this.guard = guard;
            this.repair = repair;
            this.schema = schema;
            this.bundleMapper = bundleMapper;
            this.validator = validator;
            this.artifacts = artifacts;
            this.chunkExtraction = Objects.requireNonNull(chunkExtraction, "chunkExtraction");
            this.signalGateConfig = chunkExtraction.config();
            this.signalFeatures = new StructuralChunkSignalFeatureExtractor(signalGateConfig);
        }

        @Override
        public ProcessingStage stage() {
            return ProcessingStage.EXTRACT;
        }

        @Override
        public StageExecutionResult execute(AiJob job, Instant now) {
            long t0 = System.nanoTime();
            try {
                List<SegmentInput> normalized = normalizer.normalize(loadSegments(segments, job));
                ModelDescriptor descriptor = modelRuntime.descriptor();
                ChunkingConfig config = ChunkingConfig.productionDefaults(descriptor.contextWindowTokens())
                        .withMaxOutput(MeetingLlmBudgets.EXTRACTION_MAX_TOKENS);
                List<TranscriptChunk> chunks = chunker.chunk(normalized, config);
                int index = job.chunkIndex().orElse(0);
                if (index < 0 || index >= chunks.size()) {
                    return StageExecutionResult.failure(
                            job, false, "CHUNK_INDEX_OOB", "chunk index out of bounds",
                            (System.nanoTime() - t0) / 1_000_000L, now);
                }
                TranscriptChunk chunk = chunks.get(index);
                ChunkSignalSummary previous = ChunkSignalSummary.empty();
                ChunkSignalSummary next = null;
                if (index > 0) {
                    ChunkContext prevCtx = ChunkContext.of(signalGateConfig, job.language());
                    previous = signalFeatures.extract(chunks.get(index - 1), prevCtx).toSummary();
                }
                if (index + 1 < chunks.size()) {
                    ChunkContext nextCtx = ChunkContext.of(signalGateConfig, job.language());
                    next = signalFeatures.extract(chunks.get(index + 1), nextCtx).toSummary();
                }
                ChunkContext context = ChunkContext.withNeighbors(
                        signalGateConfig, job.language(), previous, next);
                int[] tokens = new int[]{0, 0};
                ChunkExtractionResult result = chunkExtraction.extract(chunk, context, c -> {
                    PublishedPrompt prompt = prompts.requirePublished(
                            com.nanobaseai.actenora.aiprocessing.infrastructure.prompt.InMemoryPromptRegistry.DEFAULT_EXTRACTION_PROMPT_ID);
                    String system = ExtractionPromptRules.systemRulesFor(job.language());
                    String user = ExtractionPromptRules.applyLanguage(prompt.template(), job.language())
                            .replace("{{meetingTitle}}", job.meetingOccurrenceId().toString())
                            .replace("{{meetingDate}}", "")
                            .replace("{{participants}}", "")
                            .replace("{{evidenceSegmentIds}}", String.join(",", c.segmentIds()))
                            .replace("{{chunk}}", formatChunk(c));
                    guard.assertFits(
                            system + "\n" + user,
                            descriptor.contextWindowTokens(),
                            MeetingLlmBudgets.EXTRACTION_MAX_TOKENS
                    );
                    InferenceResponse response = modelRuntime.infer(new InferenceRequest(
                            InferenceTaskType.CHUNK_EXTRACTION.name(),
                            prompt.promptVersionId(),
                            prompt.outputSchemaId(),
                            system,
                            user,
                            c.segmentIds(),
                            MeetingLlmBudgets.EXTRACTION_MAX_TOKENS,
                            1800
                    ));
                    tokens[0] = clampTokens(response.inputTokens());
                    tokens[1] = clampTokens(response.outputTokens());
                    String json = response.rawText();
                    if (repair.needsRepair(json)) {
                        json = repair.repairOrThrow(json);
                    }
                    JsonNode node = schema.parseAndValidate(json);
                    ExtractionBundle bundle = ProposalCuePostProcessor.productionDefaults()
                            .process(bundleMapper.fromJson(node), c.segments());
                    validator.validate(
                            bundle,
                            new HashSet<>(c.segmentIds()),
                            c.joinedContent() + "\n" + groundingCorpus(normalized)
                    );
                    return bundle;
                });
                // Keep merge-compatible extraction schema; gate metadata lives in qualityFlags + chunk-gate artifact.
                List<String> flags = new ArrayList<>(result.bundle().qualityFlags());
                flags.add("GATE_OUTCOME:" + result.gateDecision().outcome().name());
                flags.add("GATE_POLICY:" + result.gateDecision().policyVersion());
                flags.add("GATE_SCORE:" + result.gateDecision().score());
                ExtractionBundle flagged = new ExtractionBundle(
                        result.bundle().topics(),
                        result.bundle().decisions(),
                        result.bundle().actionItems(),
                        result.bundle().risks(),
                        result.bundle().openQuestions(),
                        result.bundle().commitments(),
                        result.bundle().issues(),
                        result.bundle().proposals(),
                        result.bundle().importantFacts(),
                        flags,
                        result.bundle().evidenceSegmentIds(),
                        result.bundle().confidence()
                );
                JsonNode outNode = MAPPER.valueToTree(flagged);
                String out = MAPPER.writeValueAsString(outNode);
                try {
                    schema.parseAndValidate(out);
                } catch (RuntimeException schemaEx) {
                    out = emptyExtractionJson(flags);
                }
                ObjectNode gateNode = MAPPER.createObjectNode();
                gateNode.put("outcome", result.gateDecision().outcome().name());
                gateNode.put("score", result.gateDecision().score());
                gateNode.put("policyVersion", result.gateDecision().policyVersion());
                gateNode.put("estimatedTokensSaved", result.gateDecision().estimatedTokensSaved());
                gateNode.put("skippedWithoutInfer", result.skippedWithoutInfer());
                gateNode.set("reasons", MAPPER.valueToTree(result.gateDecision().reasons()));
                artifacts.save(ProcessingArtifact.inlineJson(
                        job.tenantId(),
                        job.id(),
                        job.meetingOccurrenceId(),
                        "chunk-gate-" + index,
                        MAPPER.writeValueAsString(gateNode),
                        now
                ));
                return StageExecutionResult.success(
                        job, "chunk-extraction-" + index, out,
                        tokens[0], tokens[1],
                        (System.nanoTime() - t0) / 1_000_000L, now);
            } catch (Exception ex) {
                return StageExecutionResult.failure(
                        job, true, "EXTRACT_FAILED", safe(ex.getMessage()),
                        (System.nanoTime() - t0) / 1_000_000L, now);
            }
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

        private static String emptyExtractionJson(List<String> qualityFlags) throws Exception {
            ObjectNode root = MAPPER.createObjectNode();
            root.putArray("topics");
            root.putArray("decisions");
            root.putArray("actionItems");
            root.putArray("risks");
            root.putArray("openQuestions");
            root.putArray("commitments");
            root.putArray("issues");
            root.putArray("proposals");
            root.putArray("importantFacts");
            ArrayNode flags = root.putArray("qualityFlags");
            for (String flag : qualityFlags) {
                flags.add(flag);
            }
            root.putArray("evidenceSegmentIds");
            root.put("confidence", 0.0d);
            return MAPPER.writeValueAsString(root);
        }
    }

    static final class MergeExecutor implements StageExecutor {
        private final ModelRuntimePort modelRuntime;
        private final PromptRegistryPort prompts;
        private final ProcessingArtifactRepository artifacts;
        private final ExtractionMerger merger;
        private final LimitedJsonRepair repair;
        private final ExtractionJsonSchemaValidator schema;
        private final ExtractionBundleMapper bundleMapper;
        private final TranscriptSegmentSourcePort segments;
        private final SegmentNormalizer normalizer;

        MergeExecutor(
                ModelRuntimePort modelRuntime,
                PromptRegistryPort prompts,
                ProcessingArtifactRepository artifacts,
                ExtractionMerger merger,
                LimitedJsonRepair repair,
                ExtractionJsonSchemaValidator schema,
                ExtractionBundleMapper bundleMapper,
                TranscriptSegmentSourcePort segments,
                SegmentNormalizer normalizer
        ) {
            this.modelRuntime = modelRuntime;
            this.prompts = prompts;
            this.artifacts = artifacts;
            this.merger = merger;
            this.repair = repair;
            this.schema = schema;
            this.bundleMapper = bundleMapper;
            this.segments = segments;
            this.normalizer = normalizer;
        }

        @Override
        public ProcessingStage stage() {
            return ProcessingStage.MERGE;
        }

        @Override
        public StageExecutionResult execute(AiJob job, Instant now) {
            long t0 = System.nanoTime();
            try {
                List<ProcessingArtifact> chunkArts = artifacts.findByParentMeetingAndType(
                        job.tenantId(), job.meetingOccurrenceId(), "chunk-extraction-0");
                // Collect all chunk-extraction-* artifacts
                List<ExtractionBundle> bundles = new ArrayList<>();
                for (int i = 0; i < 64; i++) {
                    Optional<String> payload = artifacts
                            .findLatestByMeetingAndType(job.tenantId(), job.meetingOccurrenceId(), "chunk-extraction-" + i)
                            .flatMap(ProcessingArtifact::payloadJson);
                    if (payload.isEmpty()) {
                        if (i == 0 && chunkArts.isEmpty()) {
                            break;
                        }
                        if (i > 0) {
                            break;
                        }
                    } else {
                        JsonNode node = schema.parseAndValidate(payload.get());
                        bundles.add(bundleMapper.fromJson(node));
                    }
                }
                if (bundles.isEmpty()) {
                    return StageExecutionResult.failure(
                            job, false, "NO_CHUNK_BUNDLES", "no chunk extraction artifacts",
                            (System.nanoTime() - t0) / 1_000_000L, now);
                }
                List<SegmentInput> normalized = normalizer.normalize(loadSegments(segments, job));
                ExtractionBundle merged = new CrossTypeConsistencyAuditor().auditBundle(
                        CrossTypeMeetingItemScrubber.productionDefaults().scrub(
                                ProposalCuePostProcessor.productionDefaults().process(
                                        new MeasuredObservationFactSeeder().seed(merger.merge(bundles), normalized),
                                        normalized)));
                String cleanedJson = MAPPER.writeValueAsString(MAPPER.valueToTree(merged));
                String deterministicJson = MAPPER.writeValueAsString(Map.of(
                        "mergedDeterministic", true,
                        "topicCount", merged.topics() == null ? 0 : merged.topics().size()
                ));

                // LLM candidate merge / contradiction pass (large model)
                String system = ExtractionPromptRules.systemRulesFor(job.language())
                        + "\n\nTASK: Merge candidates, resolve contradictions, dedupe decisions and actions. JSON only.";
                String user = "Deterministic merge summary:\n" + deterministicJson
                        + "\nCandidate bundles count: " + bundles.size()
                        + "\nReturn the consolidated extraction JSON schema.";
                InferenceResponse response = modelRuntime.infer(new InferenceRequest(
                        InferenceTaskType.CANDIDATE_MERGE.name(),
                        job.promptVersion(),
                        job.schemaVersion(),
                        system,
                        user,
                        List.of(),
                        MeetingLlmBudgets.MERGE_MAX_TOKENS,
                        1800
                ));
                String json = response.rawText();
                if (repair.needsRepair(json)) {
                    json = repair.repairOrThrow(json);
                }
                // Prefer LLM output when schema-valid; else keep scrubbed deterministic merge.
                try {
                    JsonNode llmNode = schema.parseAndValidate(json);
                    ExtractionBundle llmBundle = new CrossTypeConsistencyAuditor().auditBundle(
                            CrossTypeMeetingItemScrubber.productionDefaults().scrub(
                                    ProposalCuePostProcessor.productionDefaults().process(
                                            bundleMapper.fromJson(llmNode), normalized)));
                    json = MAPPER.writeValueAsString(MAPPER.valueToTree(llmBundle));
                } catch (RuntimeException ex) {
                    json = cleanedJson;
                }
                return StageExecutionResult.success(
                        job, "merged-bundle", json,
                        clampTokens(response.inputTokens()), clampTokens(response.outputTokens()),
                        (System.nanoTime() - t0) / 1_000_000L, now);
            } catch (Exception ex) {
                return StageExecutionResult.failure(
                        job, true, "MERGE_FAILED", safe(ex.getMessage()),
                        (System.nanoTime() - t0) / 1_000_000L, now);
            }
        }
    }

    static final class ValidateExecutor implements StageExecutor {
        private final ProcessingArtifactRepository artifacts;
        private final DeterministicExtractionValidator validator;
        private final TranscriptSegmentSourcePort segments;
        private final SegmentNormalizer normalizer;

        ValidateExecutor(
                ProcessingArtifactRepository artifacts,
                DeterministicExtractionValidator validator,
                TranscriptSegmentSourcePort segments,
                SegmentNormalizer normalizer
        ) {
            this.artifacts = artifacts;
            this.validator = validator;
            this.segments = segments;
            this.normalizer = normalizer;
        }

        @Override
        public ProcessingStage stage() {
            return ProcessingStage.VALIDATE;
        }

        @Override
        public StageExecutionResult execute(AiJob job, Instant now) {
            long t0 = System.nanoTime();
            try {
                String merged = latestArtifact(artifacts, job, "merged-bundle")
                        .orElseThrow(() -> new IllegalStateException("missing merged-bundle"));
                ExtractionJsonSchemaValidator schema = new ExtractionJsonSchemaValidator();
                ExtractionBundleMapper mapper = new ExtractionBundleMapper();
                ExtractionBundle bundle = mapper.fromJson(schema.parseAndValidate(merged));
                List<SegmentInput> normalized = normalizer.normalize(loadSegments(segments, job));
                Set<String> allowed = normalized.stream().map(SegmentInput::segmentId).collect(Collectors.toCollection(HashSet::new));
                validator.validate(bundle, allowed, groundingCorpus(normalized));
                return StageExecutionResult.success(
                        job, "validated-bundle", merged, 0, 0, (System.nanoTime() - t0) / 1_000_000L, now);
            } catch (Exception ex) {
                return StageExecutionResult.failure(
                        job, false, "VALIDATE_FAILED", safe(ex.getMessage()),
                        (System.nanoTime() - t0) / 1_000_000L, now);
            }
        }
    }

    static final class MinutesExecutor implements StageExecutor {
        private final ModelRuntimePort modelRuntime;
        private final ProcessingArtifactRepository artifacts;
        private final FinalNoteAssembler noteAssembler;
        private final TranscriptSegmentSourcePort segments;
        private final SegmentNormalizer normalizer;
        private final PriorMeetingContextPort priorContext;
        private final MeetingNoteHandoffPort noteHandoff;
        private final PipelineQualityMetricsPort qualityMetrics;
        private final MeetingOccurrenceClockPort meetingClock;
        private final MinutesFinalizationPolicy finalizationPolicy;

        MinutesExecutor(
                ModelRuntimePort modelRuntime,
                ProcessingArtifactRepository artifacts,
                FinalNoteAssembler noteAssembler,
                TranscriptSegmentSourcePort segments,
                SegmentNormalizer normalizer,
                PriorMeetingContextPort priorContext,
                MeetingNoteHandoffPort noteHandoff,
                PipelineQualityMetricsPort qualityMetrics
        ) {
            this(
                    modelRuntime, artifacts, noteAssembler, segments, normalizer, priorContext, noteHandoff,
                    qualityMetrics,
                    MeetingOccurrenceClockPort.unsupported(),
                    MinutesFinalizationPolicy.compatibility()
            );
        }

        MinutesExecutor(
                ModelRuntimePort modelRuntime,
                ProcessingArtifactRepository artifacts,
                FinalNoteAssembler noteAssembler,
                TranscriptSegmentSourcePort segments,
                SegmentNormalizer normalizer,
                PriorMeetingContextPort priorContext,
                MeetingNoteHandoffPort noteHandoff,
            PipelineQualityMetricsPort qualityMetrics,
            MeetingOccurrenceClockPort meetingClock
        ) {
            this(
                    modelRuntime,
                    artifacts,
                    noteAssembler,
                    segments,
                    normalizer,
                    priorContext,
                    noteHandoff,
                    qualityMetrics,
                    meetingClock,
                    MinutesFinalizationPolicy.compatibility()
            );
        }

        MinutesExecutor(
                ModelRuntimePort modelRuntime,
                ProcessingArtifactRepository artifacts,
                FinalNoteAssembler noteAssembler,
                TranscriptSegmentSourcePort segments,
                SegmentNormalizer normalizer,
                PriorMeetingContextPort priorContext,
                MeetingNoteHandoffPort noteHandoff,
                PipelineQualityMetricsPort qualityMetrics,
                MeetingOccurrenceClockPort meetingClock,
                MinutesFinalizationPolicy finalizationPolicy
        ) {
            this.modelRuntime = modelRuntime;
            this.artifacts = artifacts;
            this.noteAssembler = noteAssembler;
            this.segments = segments;
            this.normalizer = normalizer;
            this.priorContext = priorContext;
            this.noteHandoff = noteHandoff;
            this.qualityMetrics = qualityMetrics == null ? PipelineQualityMetricsPort.noop() : qualityMetrics;
            this.meetingClock = meetingClock == null
                    ? MeetingOccurrenceClockPort.unsupported()
                    : meetingClock;
            this.finalizationPolicy = finalizationPolicy == null
                    ? MinutesFinalizationPolicy.compatibility()
                    : finalizationPolicy;
        }

        @Override
        public ProcessingStage stage() {
            return ProcessingStage.MINUTES;
        }

        @Override
        public StageExecutionResult execute(AiJob job, Instant now) {
            long t0 = System.nanoTime();
            try {
                Optional<String> validated = latestArtifact(artifacts, job, "validated-bundle");
                Optional<String> merged = latestArtifact(artifacts, job, "merged-bundle");
                String source = validated.or(() -> merged).orElse(null);
                FinalNoteDraft draft;
                int inTok = 0;
                int outTok = 0;
                int finalizationModelCalls = 0;
                long finalizationModelLatencyMs = 0;
                boolean finalizationFallbackUsed = false;
                Map<String, Object> actionPostStats = null;
                if (source == null) {
                    draft = noteAssembler.assemble(ExtractionBundle.empty(), job.language());
                } else {
                    ExtractionBundle bundle = new ExtractionBundleMapper()
                            .fromJson(new ExtractionJsonSchemaValidator().parseAndValidate(source));
                    List<SegmentInput> normalized = normalizer.normalize(loadSegments(segments, job));
                    bundle = CrossTypeMeetingItemScrubber.productionDefaults().scrub(
                            ProposalCuePostProcessor.productionDefaults().process(bundle, normalized));
                    bundle = new CrossTypeConsistencyAuditor().auditBundle(bundle);
                    ActionPostProcessingPipeline.Context actionCtx = actionContext(job, normalized);
                    bundle = ActionPostProcessingPipeline.productionDefaults().applyToBundle(bundle, actionCtx);
                    bundle = new CrossTypeMeetingItemSubsumer().applyToBundle(bundle);
                    FinalNoteDraft deterministic = noteAssembler.assemble(bundle, job.language());
                    // Same allowlist as VALIDATE / legacy ExtractionPipelineService: all transcript segment ids.
                    Set<String> allowed = normalized.stream()
                            .map(SegmentInput::segmentId)
                            .collect(Collectors.toCollection(HashSet::new));
                    PriorMeetingContext prior = priorContext
                            .load(TenantId.of(job.tenantId()), job.meetingOccurrenceId())
                            .orElse(PriorMeetingContext.EMPTY);
                    MinutesFinalizationResult finalization =
                            new MinutesSynthesisAndAudit(
                                    modelRuntime,
                                    finalizationPolicy.timeoutSeconds(),
                                    qualityMetrics,
                                    finalizationPolicy
                            ).finalizeMinutes(
                                    bundle,
                                    deterministic,
                                    allowed,
                                    job.meetingOccurrenceId().toString(),
                                    job.language(),
                                    prior
                            );
                    draft = finalization.draft();
                    inTok = clampTokens(finalization.inputTokens());
                    outTok = clampTokens(finalization.outputTokens());
                    finalizationModelCalls = finalization.modelCalls();
                    finalizationModelLatencyMs = finalization.modelLatencyMs();
                    finalizationFallbackUsed = finalization.fallbackUsed();
                    draft = new ActionContextualEnricher().enrich(draft, normalized);
                    ExplicitActionCueRecoverer.Result recovered =
                            new ExplicitActionCueRecoverer().recover(draft.actionItems(), normalized);
                    ActionPostProcessingPipeline.Result post =
                            ActionPostProcessingPipeline.productionDefaults().postProcess(
                                    recovered.actions(), draft.commitments(), actionCtx);
                    for (int i = 0; i < recovered.recovered(); i++) {
                        post.stats().incrementExplicitActionCuesRecovered();
                    }
                    draft = new FinalNoteDraft(
                            draft.executiveSummary(),
                            draft.decisions(),
                            post.actions(),
                            draft.risks(),
                            draft.openQuestions(),
                            post.commitments(),
                            draft.topics(),
                            draft.issues(),
                            draft.proposals(),
                            draft.importantFacts(),
                            mergeFlags(draft.qualityFlags(), post.qualityFlags()),
                            draft.evidenceSegmentIds(),
                            draft.confidence(),
                            draft.requiresManualReview() || post.requiresManualReview()
                    );
                    draft = new CrossTypeMeetingItemSubsumer().applyToDraft(draft);
                    draft = new CrossTypeConsistencyAuditor().audit(draft, finalizationFallbackUsed);
                    draft = FinalNoteConfidencePolicy.productionDefaults().apply(draft);
                    actionPostStats = post.stats().toArtifactMap(job.meetingOccurrenceId().toString());
                    if (artifacts != null && actionPostStats != null) {
                        try {
                            artifacts.save(ProcessingArtifact.inlineJson(
                                    job.tenantId(),
                                    job.id(),
                                    job.meetingOccurrenceId(),
                                    ActionPostProcessingStats.ARTIFACT_TYPE,
                                    MAPPER.writeValueAsString(actionPostStats),
                                    now
                            ));
                        } catch (Exception ignored) {
                            // Observability must not fail minutes stage.
                        }
                    }
                }
                var meetingStartedAt = meetingClock
                        .scheduledStart(TenantId.of(job.tenantId()), job.meetingOccurrenceId())
                        .orElse(null);
                var meetingTimezone =
                        meetingClock.timezone(TenantId.of(job.tenantId()), job.meetingOccurrenceId());
                Optional<UUID> noteId = noteHandoff.handoff(new MeetingNoteHandoffPort.HandoffCommand(
                        job.tenantId(),
                        job.meetingOccurrenceId(),
                        job.transcriptId(),
                        job.id(),
                        modelRuntime.descriptor().servedModelId(),
                        job.promptVersion(),
                        job.schemaVersion(),
                        meetingStartedAt == null ? null : meetingStartedAt.toString(),
                        meetingTimezone == null ? null : meetingTimezone.getId(),
                        draft
                ));
                java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
                payload.put("executiveSummary", draft.executiveSummary() == null ? "" : draft.executiveSummary());
                payload.put("requiresManualReview", draft.requiresManualReview());
                payload.put("meetingNoteId", noteId.map(UUID::toString).orElse(""));
                payload.put("qualityFlags", draft.qualityFlags());
                payload.put("finalizationMode", finalizationPolicy.mode().name());
                payload.put("finalizationModelCalls", finalizationModelCalls);
                payload.put("finalizationModelLatencyMs", finalizationModelLatencyMs);
                payload.put("finalizationFallbackUsed", finalizationFallbackUsed);
                if (actionPostStats != null) {
                    payload.put("actionPostProcessing", actionPostStats);
                }
                String json = MAPPER.writeValueAsString(payload);
                return StageExecutionResult.success(
                        job, "final-minutes", json, inTok, outTok, (System.nanoTime() - t0) / 1_000_000L, now);
            } catch (Exception ex) {
                return StageExecutionResult.failure(
                        job, true, "MINUTES_FAILED", safe(ex.getMessage()),
                        (System.nanoTime() - t0) / 1_000_000L, now);
            }
        }

        private ActionPostProcessingPipeline.Context actionContext(AiJob job, List<SegmentInput> normalized) {
            var start = meetingClock.scheduledStart(TenantId.of(job.tenantId()), job.meetingOccurrenceId())
                    .orElse(null);
            var zone = meetingClock.timezone(TenantId.of(job.tenantId()), job.meetingOccurrenceId());
            java.util.LinkedHashSet<String> roster = new java.util.LinkedHashSet<>(
                    ActionPostProcessingPipeline.participantsFromSegments(normalized));
            for (String name : meetingClock.participantDisplayNames(
                    TenantId.of(job.tenantId()), job.meetingOccurrenceId())) {
                if (name != null && !name.isBlank()) {
                    roster.add(name.strip());
                }
            }
            return new ActionPostProcessingPipeline.Context(
                    normalized,
                    roster,
                    start,
                    zone,
                    job.meetingOccurrenceId().toString()
            );
        }

        private static List<String> mergeFlags(List<String> base, List<String> extra) {
            java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>(base == null ? List.of() : base);
            if (extra != null) {
                out.addAll(extra);
            }
            return new ArrayList<>(out);
        }
    }

    /**
     * Runs approved-knowledge indexing for the note version carried in correlationId.
     * {@code transcriptId} on the job is the noteId (see PipelineGraphFactory.admitEmbedding).
     */
    static final class EmbeddingExecutor implements StageExecutor {
        private final ApprovedKnowledgeIndexPort knowledgeIndex;

        EmbeddingExecutor(ApprovedKnowledgeIndexPort knowledgeIndex) {
            this.knowledgeIndex = knowledgeIndex;
        }

        @Override
        public ProcessingStage stage() {
            return ProcessingStage.EMBEDDING;
        }

        @Override
        public StageExecutionResult execute(AiJob job, Instant now) {
            long t0 = System.nanoTime();
            try {
                UUID noteId = job.transcriptId();
                UUID noteVersionId = job.correlationId();
                knowledgeIndex.indexApprovedNote(
                        job.tenantId(),
                        job.meetingOccurrenceId(),
                        noteId,
                        noteVersionId
                );
                String json = "{\"status\":\"indexed\",\"noteId\":\"" + noteId
                        + "\",\"noteVersionId\":\"" + noteVersionId + "\"}";
                return StageExecutionResult.success(
                        job, "embedding-complete", json, 0, 0, (System.nanoTime() - t0) / 1_000_000L, now);
            } catch (Exception ex) {
                return StageExecutionResult.failure(
                        job, true, "EMBED_FAILED", safe(ex.getMessage()),
                        (System.nanoTime() - t0) / 1_000_000L, now);
            }
        }
    }

    private static String safe(String message) {
        if (message == null) {
            return "error";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private static int clampTokens(long tokens) {
        if (tokens <= 0L) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, tokens);
    }
}
