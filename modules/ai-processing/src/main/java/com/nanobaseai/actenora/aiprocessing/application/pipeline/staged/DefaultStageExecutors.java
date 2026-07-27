package com.nanobaseai.actenora.aiprocessing.application.pipeline.staged;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.InferenceRequest;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.PriorMeetingContext;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.InferenceResponse;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.MinutesSynthesisAndAudit;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelDescriptor;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelRuntimePort;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.PriorMeetingContextPort;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.PromptRegistryPort;
import com.nanobaseai.actenora.aiprocessing.application.port.ApprovedKnowledgeIndexPort;
import com.nanobaseai.actenora.aiprocessing.application.port.MeetingNoteHandoffPort;
import com.nanobaseai.actenora.aiprocessing.application.port.ProcessingArtifactRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.TranscriptSegmentSourcePort;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingArtifact;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingStage;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ChunkingConfig;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ContextWindowGuard;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DeterministicExtractionValidator;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionMerger;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteAssembler;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
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
        SegmentNormalizer normalizer = new SegmentNormalizer();
        TranscriptChunker chunker = new TranscriptChunker();
        ContextWindowGuard guard = new ContextWindowGuard();
        LimitedJsonRepair repair = new LimitedJsonRepair();
        ExtractionJsonSchemaValidator schema = new ExtractionJsonSchemaValidator();
        ExtractionBundleMapper bundleMapper = new ExtractionBundleMapper();
        DeterministicExtractionValidator validator = new DeterministicExtractionValidator();
        ExtractionMerger merger = new ExtractionMerger();
        FinalNoteAssembler noteAssembler = new FinalNoteAssembler();

        return Map.of(
                ProcessingStage.NORMALIZE, new NormalizeExecutor(segments, normalizer, artifacts),
                ProcessingStage.TRIAGE, new TriageExecutor(prompts, modelRuntime, segments, normalizer),
                ProcessingStage.CHUNK, new ChunkPlanExecutor(segments, normalizer, chunker, guard, modelRuntime, artifacts),
                ProcessingStage.EXTRACT, new ExtractChunkExecutor(
                        prompts, modelRuntime, segments, normalizer, chunker, guard, repair, schema, bundleMapper, validator, artifacts),
                ProcessingStage.MERGE, new MergeExecutor(modelRuntime, prompts, artifacts, merger, repair, schema, bundleMapper),
                ProcessingStage.VALIDATE, new ValidateExecutor(artifacts, validator, segments, normalizer),
                ProcessingStage.MINUTES, new MinutesExecutor(
                        modelRuntime,
                        artifacts,
                        noteAssembler,
                        priorContext == null ? PriorMeetingContextPort.noop() : priorContext,
                        noteHandoff == null ? MeetingNoteHandoffPort.noop() : noteHandoff
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
                        512,
                        120
                ));
                String json = response.rawText() == null ? "{}" : response.rawText().trim();
                boolean early = isInformationalEarlyExit(json);
                long latency = (System.nanoTime() - t0) / 1_000_000L;
                if (early) {
                    return StageExecutionResult.earlyExit(
                            job, json, response.inputTokens(), response.outputTokens(), latency, now);
                }
                return StageExecutionResult.success(
                        job, "triage", json, response.inputTokens(), response.outputTokens(), latency, now);
            } catch (RuntimeException ex) {
                // Fail open to full path on triage errors.
                String fallback = """
                        {"containsDecisions":true,"containsActions":true,"containsRisks":true,"meetingType":"MIXED","fallback":true}
                        """.trim();
                return StageExecutionResult.success(
                        job, "triage", fallback, 0, 0, (System.nanoTime() - t0) / 1_000_000L, now);
            }
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
                    .withMaxOutput(descriptor.maxOutputTokens());
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
                ProcessingArtifactRepository artifacts
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
                        .withMaxOutput(descriptor.maxOutputTokens());
                List<TranscriptChunk> chunks = chunker.chunk(normalized, config);
                int index = job.chunkIndex().orElse(0);
                if (index < 0 || index >= chunks.size()) {
                    return StageExecutionResult.failure(
                            job, false, "CHUNK_INDEX_OOB", "chunk index out of bounds",
                            (System.nanoTime() - t0) / 1_000_000L, now);
                }
                TranscriptChunk chunk = chunks.get(index);
                PublishedPrompt prompt = prompts.requirePublished(
                        com.nanobaseai.actenora.aiprocessing.infrastructure.prompt.InMemoryPromptRegistry.DEFAULT_EXTRACTION_PROMPT_ID);
                String system = ExtractionPromptRules.systemRulesFor(job.language());
                String user = ExtractionPromptRules.applyLanguage(prompt.template(), job.language())
                        .replace("{{meetingTitle}}", job.meetingOccurrenceId().toString())
                        .replace("{{meetingDate}}", "")
                        .replace("{{participants}}", "")
                        .replace("{{evidenceSegmentIds}}", String.join(",", chunk.segmentIds()))
                        .replace("{{chunk}}", formatChunk(chunk));
                guard.assertFits(system + "\n" + user, descriptor.contextWindowTokens(), descriptor.maxOutputTokens());
                InferenceResponse response = modelRuntime.infer(new InferenceRequest(
                        InferenceTaskType.CHUNK_EXTRACTION.name(),
                        prompt.promptVersionId(),
                        prompt.outputSchemaId(),
                        system,
                        user,
                        chunk.segmentIds(),
                        descriptor.maxOutputTokens(),
                        1800
                ));
                String json = response.rawText();
                if (repair.needsRepair(json)) {
                    json = repair.repairOrThrow(json);
                }
                JsonNode node = schema.parseAndValidate(json);
                ExtractionBundle bundle = bundleMapper.fromJson(node);
                validator.validate(bundle, new HashSet<>(chunk.segmentIds()), chunk.joinedContent() + "\n" + groundingCorpus(normalized));
                String out = MAPPER.writeValueAsString(node);
                return StageExecutionResult.success(
                        job, "chunk-extraction-" + index, out,
                        response.inputTokens(), response.outputTokens(),
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
    }

    static final class MergeExecutor implements StageExecutor {
        private final ModelRuntimePort modelRuntime;
        private final PromptRegistryPort prompts;
        private final ProcessingArtifactRepository artifacts;
        private final ExtractionMerger merger;
        private final LimitedJsonRepair repair;
        private final ExtractionJsonSchemaValidator schema;
        private final ExtractionBundleMapper bundleMapper;

        MergeExecutor(
                ModelRuntimePort modelRuntime,
                PromptRegistryPort prompts,
                ProcessingArtifactRepository artifacts,
                ExtractionMerger merger,
                LimitedJsonRepair repair,
                ExtractionJsonSchemaValidator schema,
                ExtractionBundleMapper bundleMapper
        ) {
            this.modelRuntime = modelRuntime;
            this.prompts = prompts;
            this.artifacts = artifacts;
            this.merger = merger;
            this.repair = repair;
            this.schema = schema;
            this.bundleMapper = bundleMapper;
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
                ExtractionBundle merged = merger.merge(bundles);
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
                        6000,
                        1800
                ));
                String json = response.rawText();
                if (repair.needsRepair(json)) {
                    json = repair.repairOrThrow(json);
                }
                // Prefer LLM output when schema-valid; else keep deterministic bundle serialized via last chunk style
                try {
                    schema.parseAndValidate(json);
                } catch (RuntimeException ex) {
                    json = bundles.isEmpty() ? deterministicJson
                            : artifacts.findLatestByMeetingAndType(
                                    job.tenantId(), job.meetingOccurrenceId(), "chunk-extraction-0")
                            .flatMap(ProcessingArtifact::payloadJson)
                            .orElse(deterministicJson);
                }
                return StageExecutionResult.success(
                        job, "merged-bundle", json,
                        response.inputTokens(), response.outputTokens(),
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
        private final PriorMeetingContextPort priorContext;
        private final MeetingNoteHandoffPort noteHandoff;

        MinutesExecutor(
                ModelRuntimePort modelRuntime,
                ProcessingArtifactRepository artifacts,
                FinalNoteAssembler noteAssembler,
                PriorMeetingContextPort priorContext,
                MeetingNoteHandoffPort noteHandoff
        ) {
            this.modelRuntime = modelRuntime;
            this.artifacts = artifacts;
            this.noteAssembler = noteAssembler;
            this.priorContext = priorContext;
            this.noteHandoff = noteHandoff;
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
                if (source == null) {
                    draft = noteAssembler.assemble(ExtractionBundle.empty(), job.language());
                } else {
                    ExtractionBundle bundle = new ExtractionBundleMapper()
                            .fromJson(new ExtractionJsonSchemaValidator().parseAndValidate(source));
                    FinalNoteDraft deterministic = noteAssembler.assemble(bundle, job.language());
                    Set<String> allowed = Set.of();
                    PriorMeetingContext prior = priorContext
                            .load(TenantId.of(job.tenantId()), job.meetingOccurrenceId())
                            .orElse(PriorMeetingContext.EMPTY);
                    draft = new MinutesSynthesisAndAudit(modelRuntime, 1800)
                            .synthesizeAndAudit(
                                    bundle,
                                    deterministic,
                                    allowed,
                                    job.meetingOccurrenceId().toString(),
                                    job.language(),
                                    prior
                            );
                }
                Optional<UUID> noteId = noteHandoff.handoff(new MeetingNoteHandoffPort.HandoffCommand(
                        job.tenantId(),
                        job.meetingOccurrenceId(),
                        job.transcriptId(),
                        job.id(),
                        modelRuntime.descriptor().servedModelId(),
                        job.promptVersion(),
                        job.schemaVersion(),
                        draft
                ));
                String json = MAPPER.writeValueAsString(Map.of(
                        "executiveSummary", draft.executiveSummary() == null ? "" : draft.executiveSummary(),
                        "requiresManualReview", draft.requiresManualReview(),
                        "meetingNoteId", noteId.map(UUID::toString).orElse("")
                ));
                return StageExecutionResult.success(
                        job, "final-minutes", json, inTok, outTok, (System.nanoTime() - t0) / 1_000_000L, now);
            } catch (Exception ex) {
                return StageExecutionResult.failure(
                        job, true, "MINUTES_FAILED", safe(ex.getMessage()),
                        (System.nanoTime() - t0) / 1_000_000L, now);
            }
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
}
