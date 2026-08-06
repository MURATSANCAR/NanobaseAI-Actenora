package com.nanobaseai.actenora.aiprocessing.application.pipeline;

import com.nanobaseai.actenora.aiprocessing.application.port.PipelineQualityMetricsPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ContextWindowGuard;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.EvidenceNearMissConfig;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.EvidenceReferenceScrubber;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ImportantFactCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.IssueCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.MeetingLlmBudgets;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ProposalCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.RiskCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TopicCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.composer.GlobalComposition;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.composer.GlobalCompositionAuditor;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.composer.GlobalLedgerMerger;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.composer.GlobalMinutesComposer;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.composer.TranscriptDigest;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.composer.TranscriptDigestBuilder;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.composer.VerifiedMinutesRenderer;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency.CrossTypeConsistencyAuditor;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency.OpenQuestionHygieneFilter;
import com.nanobaseai.actenora.aiprocessing.domain.prompt.ExtractionPromptRules;
import com.nanobaseai.actenora.aiprocessing.domain.prompt.OutputLanguagePolicy;
import com.nanobaseai.actenora.aiprocessing.domain.routing.InferenceTaskType;
import com.nanobaseai.actenora.aiprocessing.infrastructure.json.ExtractionBundleMapper;
import com.nanobaseai.actenora.aiprocessing.infrastructure.json.ExtractionJsonSchemaValidator;
import com.nanobaseai.actenora.aiprocessing.infrastructure.json.LimitedJsonRepair;
import com.nanobaseai.actenora.aiprocessing.infrastructure.prompt.InMemoryPromptRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Call-2 synthesizer + Call-3 evidence audit for the evidence-based minutes pipeline.
 * Falls back to the provided deterministic draft when LLM synthesis/audit fails.
 */
public final class MinutesSynthesisAndAudit {

    private static final Logger LOG = Logger.getLogger(MinutesSynthesisAndAudit.class.getName());
    private static final AtomicLong SYNTHESIS_FALLBACKS = new AtomicLong();
    private static final AtomicLong AUDIT_FALLBACKS = new AtomicLong();

    private final ModelRuntimePort modelRuntime;
    private final LimitedJsonRepair jsonRepair;
    private final ExtractionJsonSchemaValidator schemaValidator;
    private final ExtractionBundleMapper bundleMapper;
    private final ObjectMapper objectMapper;
    private final String finalMinutesTemplate;
    private final String evidenceAuditTemplate;
    private final String editorialSummaryTemplate;
    private final int timeoutSeconds;
    private final PipelineQualityMetricsPort qualityMetrics;
    private final MinutesFinalizationPolicy finalizationPolicy;

    public MinutesSynthesisAndAudit(ModelRuntimePort modelRuntime) {
        this(modelRuntime, 0);
    }

    public MinutesSynthesisAndAudit(ModelRuntimePort modelRuntime, int timeoutSeconds) {
        this(modelRuntime, timeoutSeconds, PipelineQualityMetricsPort.noop());
    }

    public MinutesSynthesisAndAudit(
            ModelRuntimePort modelRuntime,
            int timeoutSeconds,
            PipelineQualityMetricsPort qualityMetrics
    ) {
        this(modelRuntime, timeoutSeconds, qualityMetrics, MinutesFinalizationPolicy.compatibility());
    }

    public MinutesSynthesisAndAudit(
            ModelRuntimePort modelRuntime,
            int timeoutSeconds,
            PipelineQualityMetricsPort qualityMetrics,
            MinutesFinalizationPolicy finalizationPolicy
    ) {
        this(
                modelRuntime,
                new LimitedJsonRepair(),
                new ExtractionJsonSchemaValidator(),
                new ExtractionBundleMapper(),
                new ObjectMapper(),
                loadTemplate("/aiprocessing/prompts/final-minutes.v1.txt"),
                loadTemplate("/aiprocessing/prompts/evidence-audit.v1.txt"),
                timeoutSeconds,
                qualityMetrics,
                finalizationPolicy
        );
    }

    MinutesSynthesisAndAudit(
            ModelRuntimePort modelRuntime,
            LimitedJsonRepair jsonRepair,
            ExtractionJsonSchemaValidator schemaValidator,
            ExtractionBundleMapper bundleMapper,
            ObjectMapper objectMapper,
            String finalMinutesTemplate,
            String evidenceAuditTemplate
    ) {
        this(
                modelRuntime,
                jsonRepair,
                schemaValidator,
                bundleMapper,
                objectMapper,
                finalMinutesTemplate,
                evidenceAuditTemplate,
                0,
                PipelineQualityMetricsPort.noop(),
                MinutesFinalizationPolicy.compatibility()
        );
    }

    MinutesSynthesisAndAudit(
            ModelRuntimePort modelRuntime,
            LimitedJsonRepair jsonRepair,
            ExtractionJsonSchemaValidator schemaValidator,
            ExtractionBundleMapper bundleMapper,
            ObjectMapper objectMapper,
            String finalMinutesTemplate,
            String evidenceAuditTemplate,
            int timeoutSeconds
    ) {
        this(
                modelRuntime,
                jsonRepair,
                schemaValidator,
                bundleMapper,
                objectMapper,
                finalMinutesTemplate,
                evidenceAuditTemplate,
                timeoutSeconds,
                PipelineQualityMetricsPort.noop(),
                MinutesFinalizationPolicy.compatibility()
        );
    }

    MinutesSynthesisAndAudit(
            ModelRuntimePort modelRuntime,
            LimitedJsonRepair jsonRepair,
            ExtractionJsonSchemaValidator schemaValidator,
            ExtractionBundleMapper bundleMapper,
            ObjectMapper objectMapper,
            String finalMinutesTemplate,
            String evidenceAuditTemplate,
            int timeoutSeconds,
            PipelineQualityMetricsPort qualityMetrics
    ) {
        this(
                modelRuntime,
                jsonRepair,
                schemaValidator,
                bundleMapper,
                objectMapper,
                finalMinutesTemplate,
                evidenceAuditTemplate,
                timeoutSeconds,
                qualityMetrics,
                MinutesFinalizationPolicy.compatibility()
        );
    }

    MinutesSynthesisAndAudit(
            ModelRuntimePort modelRuntime,
            LimitedJsonRepair jsonRepair,
            ExtractionJsonSchemaValidator schemaValidator,
            ExtractionBundleMapper bundleMapper,
            ObjectMapper objectMapper,
            String finalMinutesTemplate,
            String evidenceAuditTemplate,
            int timeoutSeconds,
            PipelineQualityMetricsPort qualityMetrics,
            MinutesFinalizationPolicy finalizationPolicy
    ) {
        this.modelRuntime = Objects.requireNonNull(modelRuntime, "modelRuntime");
        this.jsonRepair = Objects.requireNonNull(jsonRepair, "jsonRepair");
        this.schemaValidator = Objects.requireNonNull(schemaValidator, "schemaValidator");
        this.bundleMapper = Objects.requireNonNull(bundleMapper, "bundleMapper");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.finalMinutesTemplate = Objects.requireNonNull(finalMinutesTemplate, "finalMinutesTemplate");
        this.evidenceAuditTemplate = Objects.requireNonNull(evidenceAuditTemplate, "evidenceAuditTemplate");
        this.finalizationPolicy = Objects.requireNonNull(finalizationPolicy, "finalizationPolicy");
        this.editorialSummaryTemplate = finalizationPolicy.mode() == MinutesFinalizationPolicy.Mode.EDITORIAL
                || finalizationPolicy.mode() == MinutesFinalizationPolicy.Mode.COMPOSER
                ? loadTemplate(finalizationPolicy.promptResource())
                : "";
        if (timeoutSeconds < 0) {
            throw new IllegalArgumentException("timeoutSeconds must be >= 0");
        }
        this.timeoutSeconds = finalizationPolicy.timeoutSeconds() > 0
                ? finalizationPolicy.timeoutSeconds()
                : timeoutSeconds;
        this.qualityMetrics = qualityMetrics == null ? PipelineQualityMetricsPort.noop() : qualityMetrics;
    }

    public FinalNoteDraft synthesizeAndAudit(
            ExtractionBundle merged,
            FinalNoteDraft deterministicDraft,
            Set<String> allowedEvidenceIds,
            String meetingTitle
    ) {
        return synthesizeAndAudit(merged, deterministicDraft, allowedEvidenceIds, meetingTitle, "tr");
    }

    public FinalNoteDraft synthesizeAndAudit(
            ExtractionBundle merged,
            FinalNoteDraft deterministicDraft,
            Set<String> allowedEvidenceIds,
            String meetingTitle,
            String language
    ) {
        return synthesizeAndAudit(
                merged, deterministicDraft, allowedEvidenceIds, meetingTitle, language, PriorMeetingContext.EMPTY);
    }

    public FinalNoteDraft synthesizeAndAudit(
            ExtractionBundle merged,
            FinalNoteDraft deterministicDraft,
            Set<String> allowedEvidenceIds,
            String meetingTitle,
            String language,
            PriorMeetingContext priorMeetingContext
    ) {
        return finalizeMinutes(
                merged,
                deterministicDraft,
                allowedEvidenceIds,
                meetingTitle,
                language,
                priorMeetingContext
        ).draft();
    }

    public MinutesFinalizationResult finalizeMinutes(
            ExtractionBundle merged,
            FinalNoteDraft deterministicDraft,
            Set<String> allowedEvidenceIds,
            String meetingTitle,
            String language,
            PriorMeetingContext priorMeetingContext
    ) {
        return finalizeMinutes(
                merged,
                deterministicDraft,
                allowedEvidenceIds,
                meetingTitle,
                language,
                priorMeetingContext,
                List.of(),
                Set.of()
        );
    }

    public MinutesFinalizationResult finalizeMinutes(
            ExtractionBundle merged,
            FinalNoteDraft deterministicDraft,
            Set<String> allowedEvidenceIds,
            String meetingTitle,
            String language,
            PriorMeetingContext priorMeetingContext,
            List<SegmentInput> segments,
            Set<String> roster
    ) {
        Objects.requireNonNull(merged, "merged");
        Objects.requireNonNull(deterministicDraft, "deterministicDraft");
        Objects.requireNonNull(allowedEvidenceIds, "allowedEvidenceIds");
        PriorMeetingContext prior = priorMeetingContext == null ? PriorMeetingContext.EMPTY : priorMeetingContext;
        List<SegmentInput> segs = segments == null ? List.of() : segments;
        Set<String> people = roster == null ? Set.of() : roster;
        return switch (finalizationPolicy.mode()) {
            case DETERMINISTIC -> MinutesFinalizationResult.of(
                    scrubDraftEvidence(deterministicDraft, allowedEvidenceIds),
                    MinutesFinalizationPolicy.Mode.DETERMINISTIC.name(),
                    MinutesFinalizationPolicy.Mode.DETERMINISTIC.name(),
                    null,
                    0,
                    0,
                    0,
                    0
            );
            case EDITORIAL -> withRequestedMode(
                    editorialFinalize(
                            deterministicDraft,
                            allowedEvidenceIds,
                            meetingTitle,
                            language
                    ),
                    MinutesFinalizationPolicy.Mode.EDITORIAL.name()
            );
            case FULL -> {
                StepResult synthesized = synthesize(
                        merged,
                        deterministicDraft,
                        allowedEvidenceIds,
                        meetingTitle,
                        language,
                        prior
                );
                StepResult audited = audit(synthesized.draft(), allowedEvidenceIds, language);
                yield MinutesFinalizationResult.of(
                        scrubDraftEvidence(audited.draft(), allowedEvidenceIds),
                        MinutesFinalizationPolicy.Mode.FULL.name(),
                        MinutesFinalizationPolicy.Mode.FULL.name(),
                        synthesized.fallbackUsed() || audited.fallbackUsed() ? "FULL_STAGE_FALLBACK" : null,
                        synthesized.modelCalls() + audited.modelCalls(),
                        synthesized.inputTokens() + audited.inputTokens(),
                        synthesized.outputTokens() + audited.outputTokens(),
                        synthesized.modelLatencyMs() + audited.modelLatencyMs()
                );
            }
            case COMPOSER -> composerFinalize(
                    merged,
                    deterministicDraft,
                    allowedEvidenceIds,
                    meetingTitle,
                    language,
                    segs,
                    people
            );
        };
    }

    private static MinutesFinalizationResult withRequestedMode(MinutesFinalizationResult result, String requested) {
        return MinutesFinalizationResult.of(
                result.draft(),
                requested,
                result.effectiveMode() == null ? result.mode() : result.effectiveMode(),
                result.fallbackReason(),
                result.modelCalls(),
                result.inputTokens(),
                result.outputTokens(),
                result.modelLatencyMs()
        );
    }

    private MinutesFinalizationResult composerFinalize(
            ExtractionBundle groundedLedger,
            FinalNoteDraft deterministicDraft,
            Set<String> allowedEvidenceIds,
            String meetingTitle,
            String language,
            List<SegmentInput> segments,
            Set<String> roster
    ) {
        String requested = MinutesFinalizationPolicy.Mode.COMPOSER.name();
        if (segments.isEmpty()) {
            MinutesFinalizationResult editorial = editorialFinalize(
                    deterministicDraft, allowedEvidenceIds, meetingTitle, language);
            return MinutesFinalizationResult.of(
                    addFlag(editorial.draft(), "COMPOSER_FALLBACK"),
                    requested,
                    MinutesFinalizationPolicy.Mode.EDITORIAL.name(),
                    "DIGEST_UNAVAILABLE",
                    editorial.modelCalls(),
                    editorial.inputTokens(),
                    editorial.outputTokens(),
                    editorial.modelLatencyMs()
            );
        }
        try {
            TranscriptDigest digest = new TranscriptDigestBuilder().build(segments);
            GlobalMinutesComposer composer = new GlobalMinutesComposer(modelRuntime, timeoutSeconds);
            GlobalComposition composition = composer.compose(digest, groundedLedger);
            GlobalCompositionAuditor.VerifiedComposition verified =
                    new GlobalCompositionAuditor().verify(composition, segments, roster, allowedEvidenceIds);
            if (verified.highRejection()) {
                MinutesFinalizationResult editorial = editorialFinalize(
                        deterministicDraft, allowedEvidenceIds, meetingTitle, language);
                return MinutesFinalizationResult.of(
                        addFlag(editorial.draft(), GlobalCompositionAuditor.FLAG_COMPOSER_HIGH_REJECTION),
                        requested,
                        MinutesFinalizationPolicy.Mode.EDITORIAL.name(),
                        "COMPOSER_HIGH_EVIDENCE_REJECTION",
                        editorial.modelCalls(),
                        editorial.inputTokens(),
                        editorial.outputTokens(),
                        editorial.modelLatencyMs()
                );
            }
            GlobalLedgerMerger merger = new GlobalLedgerMerger();
            ExtractionBundle unioned = merger.unionAndDedupe(groundedLedger, verified.acceptedItems());
            FinalNoteDraft accepted = merger.toDraft(
                    unioned,
                    verified.meetingFrame() == null ? "" : verified.meetingFrame().text(),
                    !verified.qualityFlags().isEmpty()
            );
            List<String> hygieneFlags = new ArrayList<>();
            accepted = withOpenQuestions(
                    accepted,
                    new OpenQuestionHygieneFilter().filter(
                            accepted.openQuestions(),
                            accepted.decisions(),
                            accepted.actionItems(),
                            accepted.commitments(),
                            hygieneFlags
                    ),
                    hygieneFlags
            );
            accepted = new CrossTypeConsistencyAuditor().audit(accepted, false);
            FinalNoteDraft rendered = new VerifiedMinutesRenderer()
                    .assertProseConsistent(
                            new VerifiedMinutesRenderer().renderDeterministic(accepted, verified.meetingFrame()));
            // Prefer editorial polish when configured prompt is available; prose still from accepted ledger only.
            if (editorialSummaryTemplate != null && !editorialSummaryTemplate.isBlank()) {
                try {
                    MinutesFinalizationResult polished = editorialFinalize(
                            rendered, allowedEvidenceIds, meetingTitle, language);
                    FinalNoteDraft consistent = new VerifiedMinutesRenderer()
                            .assertProseConsistent(polished.draft());
                    List<String> flags = new ArrayList<>(consistent.qualityFlags());
                    flags.addAll(verified.qualityFlags());
                    flags.add("COMPOSER_EFFECTIVE");
                    consistent = withFlags(consistent, flags);
                    return MinutesFinalizationResult.of(
                            scrubDraftEvidence(consistent, allowedEvidenceIds),
                            requested,
                            requested,
                            null,
                            polished.modelCalls() + 1,
                            polished.inputTokens(),
                            polished.outputTokens(),
                            polished.modelLatencyMs()
                    );
                } catch (RuntimeException ex) {
                    FinalNoteDraft fallback = new VerifiedMinutesRenderer()
                            .assertProseConsistent(rendered);
                    fallback = addFlag(fallback, "COMPOSER_RENDER_FALLBACK");
                    return MinutesFinalizationResult.of(
                            scrubDraftEvidence(fallback, allowedEvidenceIds),
                            requested,
                            requested,
                            "RENDERER_EDITORIAL_FAILED",
                            1,
                            0,
                            0,
                            0
                    );
                }
            }
            FinalNoteDraft out = addFlag(rendered, "COMPOSER_EFFECTIVE");
            return MinutesFinalizationResult.of(
                    scrubDraftEvidence(out, allowedEvidenceIds),
                    requested,
                    requested,
                    null,
                    1,
                    0,
                    0,
                    0
            );
        } catch (RuntimeException ex) {
            LOG.log(Level.SEVERE, "COMPOSER fallback to EDITORIAL", ex);
            MinutesFinalizationResult editorial = editorialFinalize(
                    deterministicDraft, allowedEvidenceIds, meetingTitle, language);
            return MinutesFinalizationResult.of(
                    addFlag(editorial.draft(), "COMPOSER_FALLBACK"),
                    requested,
                    MinutesFinalizationPolicy.Mode.EDITORIAL.name(),
                    "COMPOSER_" + ex.getClass().getSimpleName().toUpperCase(Locale.ROOT),
                    editorial.modelCalls(),
                    editorial.inputTokens(),
                    editorial.outputTokens(),
                    editorial.modelLatencyMs()
            );
        }
    }

    private static FinalNoteDraft addFlag(FinalNoteDraft draft, String flag) {
        List<String> flags = new ArrayList<>(draft.qualityFlags());
        if (!flags.contains(flag)) {
            flags.add(flag);
        }
        return withFlags(draft, flags);
    }

    private static FinalNoteDraft withFlags(FinalNoteDraft draft, List<String> flags) {
        return new FinalNoteDraft(
                draft.executiveSummary(),
                draft.decisions(),
                draft.actionItems(),
                draft.risks(),
                draft.openQuestions(),
                draft.commitments(),
                draft.topics(),
                draft.issues(),
                draft.proposals(),
                draft.importantFacts(),
                List.copyOf(flags),
                draft.evidenceSegmentIds(),
                draft.confidence(),
                draft.requiresManualReview()
        );
    }

    private static FinalNoteDraft withOpenQuestions(
            FinalNoteDraft draft,
            List<OpenQuestionCandidate> questions,
            List<String> extraFlags
    ) {
        List<String> flags = new ArrayList<>(draft.qualityFlags());
        for (String f : extraFlags) {
            if (!flags.contains(f)) {
                flags.add(f);
            }
        }
        return new FinalNoteDraft(
                draft.executiveSummary(),
                draft.decisions(),
                draft.actionItems(),
                draft.risks(),
                questions,
                draft.commitments(),
                draft.topics(),
                draft.issues(),
                draft.proposals(),
                draft.importantFacts(),
                List.copyOf(flags),
                draft.evidenceSegmentIds(),
                draft.confidence(),
                draft.requiresManualReview()
        );
    }

    private MinutesFinalizationResult editorialFinalize(
            FinalNoteDraft deterministicDraft,
            Set<String> allowedEvidenceIds,
            String meetingTitle,
            String language
    ) {
        InferenceResponse response = null;
        boolean inferenceAttempted = false;
        try {
            ObjectNode validatedMinutes = objectMapper.valueToTree(deterministicDraft);
            validatedMinutes.remove("executiveSummary");
            ObjectNode editorialInput = objectMapper.createObjectNode();
            editorialInput.put("outputLanguageCode", language == null ? "" : language);
            editorialInput.put("meetingTitle", meetingTitle == null ? "" : meetingTitle);
            editorialInput.set("validatedMinutes", validatedMinutes);
            String systemPrompt = editorialSummaryTemplate;
            String userPrompt = objectMapper.writeValueAsString(editorialInput);
            new ContextWindowGuard().assertFits(
                    systemPrompt + "\n" + userPrompt,
                    modelRuntime.descriptor().contextWindowTokens(),
                    finalizationPolicy.maxOutputTokens()
            );
            inferenceAttempted = true;
            response = modelRuntime.infer(new InferenceRequest(
                    finalizationPolicy.taskType(),
                    finalizationPolicy.promptVersionId(),
                    finalizationPolicy.schemaVersion(),
                    systemPrompt,
                    userPrompt,
                    List.copyOf(allowedEvidenceIds),
                    finalizationPolicy.maxOutputTokens(),
                    finalizationPolicy.timeoutSeconds()
            ));
            JsonNode root = parseEditorialJson(response.rawText());
            String summary = root.path("executiveSummary").asText().trim();
            if (summary.isBlank()) {
                throw new IllegalArgumentException("Editorial summary response is blank");
            }
            FinalNoteDraft draft = withEditorialSummary(
                    deterministicDraft,
                    summary,
                    root.path("reviewRequired").asBoolean(false)
            );
            return new MinutesFinalizationResult(
                    scrubDraftEvidence(draft, allowedEvidenceIds),
                    finalizationPolicy.mode().name(),
                    1,
                    response.inputTokens(),
                    response.outputTokens(),
                    response.latencyMs(),
                    false,
                    finalizationPolicy.mode().name(),
                    finalizationPolicy.mode().name(),
                    null
            );
        } catch (RuntimeException | IOException ex) {
            qualityMetrics.recordFallback("editorial", ex.getClass().getSimpleName());
            LOG.log(Level.SEVERE,
                    () -> "Pipeline stage failed; configured finalization fallback activated. stage=EDITORIAL"
                            + " reason=" + ex.getClass().getSimpleName());
            LOG.log(Level.SEVERE, "EDITORIAL fallback detail", ex);
            if (finalizationPolicy.failureMode() == MinutesFinalizationPolicy.FailureMode.FAIL) {
                if (ex instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException(ex);
            }
            return new MinutesFinalizationResult(
                    scrubDraftEvidence(deterministicDraft, allowedEvidenceIds),
                    finalizationPolicy.mode().name(),
                    inferenceAttempted ? 1 : 0,
                    response == null ? 0 : response.inputTokens(),
                    response == null ? 0 : response.outputTokens(),
                    response == null ? 0 : response.latencyMs(),
                    true,
                    finalizationPolicy.mode().name(),
                    finalizationPolicy.mode().name(),
                    "EDITORIAL_" + ex.getClass().getSimpleName().toUpperCase(Locale.ROOT)
            );
        }
    }

    private JsonNode parseEditorialJson(String raw) throws IOException {
        JsonNode root = parseAuditJson(raw);
        if (!root.isObject()
                || !root.path("executiveSummary").isTextual()
                || !root.path("reviewRequired").isBoolean()) {
            throw new IllegalArgumentException("Editorial response does not match the configured schema");
        }
        return root;
    }

    private static FinalNoteDraft withEditorialSummary(
            FinalNoteDraft source,
            String summary,
            boolean reviewRequired
    ) {
        // Do not prepend deterministic agenda/outcome dump onto editorial prose.
        return new FinalNoteDraft(
                summary,
                source.decisions(),
                source.actionItems(),
                source.risks(),
                source.openQuestions(),
                source.commitments(),
                source.topics(),
                source.issues(),
                source.proposals(),
                source.importantFacts(),
                source.qualityFlags(),
                source.evidenceSegmentIds(),
                source.confidence(),
                source.requiresManualReview() || reviewRequired
        );
    }

    private StepResult synthesize(
            ExtractionBundle merged,
            FinalNoteDraft fallback,
            Set<String> allowedEvidenceIds,
            String meetingTitle,
            String language,
            PriorMeetingContext priorMeetingContext
    ) {
        InferenceResponse response = null;
        boolean inferenceAttempted = false;
        try {
            String candidatesJson = objectMapper.writeValueAsString(toCandidateNode(merged));
            String priorBlock = priorMeetingContext.toPromptBlock();
            String userPrompt = ExtractionPromptRules.applyLanguage(finalMinutesTemplate, language)
                    .replace("{{meetingTitle}}", meetingTitle == null ? "" : meetingTitle)
                    .replace("{{candidatesJson}}", candidatesJson)
                    .replace("{{evidenceSegmentIds}}", String.join(",", allowedEvidenceIds))
                    .replace(
                            "{{priorMeetingContext}}",
                            priorBlock.isBlank() ? "(yok)" : priorBlock
                    );
            inferenceAttempted = true;
            response = modelRuntime.infer(new InferenceRequest(
                    InferenceTaskType.FINAL_NOTE.name(),
                    "pv-meeting-final-note-v1",
                    InMemoryPromptRegistry.FINAL_NOTE_PROMPT_ID,
                    ExtractionPromptRules.systemRulesFor(language),
                    userPrompt,
                    List.copyOf(allowedEvidenceIds),
                    MeetingLlmBudgets.FINAL_MAX_TOKENS,
                    timeoutSeconds
            ));
            JsonNode node = parseSynthesisJson(response.rawText());
            ExtractionBundle bundle = stripUnknownEvidence(bundleMapper.fromJson(node), allowedEvidenceIds);
            String summary = OutputLanguagePolicy.sanitizeUserFacingText(
                    textOr(node.path("executiveSummary").asText(null), fallback.executiveSummary()),
                    language
            );
            List<String> flags = new ArrayList<>(fallback.qualityFlags());
            for (String flag : bundle.qualityFlags()) {
                if (!flags.contains(flag)) {
                    flags.add(flag);
                }
            }
            flags.add("LLM_SYNTHESIZED");
            if (node.path("reviewRequired").asBoolean(false) && !flags.contains("NEEDS_REVIEW")) {
                flags.add("NEEDS_REVIEW");
            }
            boolean manual = fallback.requiresManualReview()
                    || flags.stream().anyMatch(f -> f.toUpperCase(Locale.ROOT).contains("NEEDS_REVIEW"))
                    || node.path("reviewRequired").asBoolean(false);
            // Final-minutes models often omit proposal cues; keep deterministic/seeded recoveries.
            List<ProposalCandidate> proposals = preferNonEmpty(bundle.proposals(), fallback.proposals());
            // Open questions: models frequently return a partial list. Union with
            // pre-synthesis candidates so recall does not collapse to the LLM subset.
            List<OpenQuestionCandidate> openQuestions = unionOpenQuestions(
                    bundle.openQuestions(), fallback.openQuestions());
            // Important facts: synthesis often drops measured observations (n/m error rates).
            List<ImportantFactCandidate> importantFacts = unionImportantFacts(
                    bundle.importantFacts(), fallback.importantFacts());
            return successfulStep(
                    new FinalNoteDraft(
                            summary,
                            preferNonEmpty(bundle.decisions(), fallback.decisions()),
                            preferActionsPreserveDates(bundle.actionItems(), fallback.actionItems()),
                            preferNonEmpty(bundle.risks(), fallback.risks()),
                            openQuestions,
                            preferNonEmpty(bundle.commitments(), fallback.commitments()),
                            preferNonEmpty(bundle.topics(), fallback.topics()),
                            preferNonEmpty(bundle.issues(), fallback.issues()),
                            proposals,
                            importantFacts,
                            flags,
                            bundle.evidenceSegmentIds().isEmpty()
                                    ? fallback.evidenceSegmentIds()
                                    : bundle.evidenceSegmentIds(),
                            bundle.confidence() > 0 ? bundle.confidence() : fallback.confidence(),
                            manual
                    ),
                    response
            );
        } catch (RuntimeException | IOException ex) {
            SYNTHESIS_FALLBACKS.incrementAndGet();
            qualityMetrics.recordFallback("synthesis", ex.getClass().getSimpleName());
            LOG.log(Level.SEVERE,
                    () -> "Pipeline stage failed; fallback activated. stage=SYNTHESIS meetingTitle="
                            + meetingTitle
                            + " reason=" + ex.getClass().getSimpleName());
            LOG.log(Level.SEVERE, "SYNTHESIS fallback detail", ex);
            List<String> flags = new ArrayList<>(fallback.qualityFlags());
            flags.add("SYNTHESIS_FALLBACK");
            return fallbackStep(
                    new FinalNoteDraft(
                            fallback.executiveSummary(),
                            fallback.decisions(),
                            fallback.actionItems(),
                            fallback.risks(),
                            fallback.openQuestions(),
                            fallback.commitments(),
                            fallback.topics(),
                            fallback.issues(),
                            fallback.proposals(),
                            fallback.importantFacts(),
                            flags,
                            fallback.evidenceSegmentIds(),
                            fallback.confidence(),
                            fallback.requiresManualReview()
                    ),
                    response,
                    inferenceAttempted
            );
        }
    }

    private StepResult audit(FinalNoteDraft draft, Set<String> allowedEvidenceIds, String language) {
        InferenceResponse response = null;
        boolean inferenceAttempted = false;
        try {
            String candidatesJson = objectMapper.writeValueAsString(toDraftNode(draft));
            String userPrompt = ExtractionPromptRules.applyLanguage(evidenceAuditTemplate, language)
                    .replace("{{candidatesJson}}", candidatesJson)
                    .replace("{{evidenceSegmentIds}}", String.join(",", allowedEvidenceIds));
            inferenceAttempted = true;
            response = modelRuntime.infer(new InferenceRequest(
                    InferenceTaskType.VALIDATION.name(),
                    "pv-meeting-validation-v1",
                    InMemoryPromptRegistry.VALIDATION_PROMPT_ID,
                    ExtractionPromptRules.systemRulesFor(language),
                    userPrompt,
                    List.copyOf(allowedEvidenceIds),
                    MeetingLlmBudgets.AUDIT_MAX_TOKENS,
                    timeoutSeconds
            ));
            JsonNode root = parseAuditJson(response.rawText());
            Set<AuditItemKey> expected = expectedAuditItems(draft);
            Set<AuditItemKey> seen = new HashSet<>();
            Set<AuditItemKey> unsupported = new HashSet<>();
            Set<AuditItemKey> partial = new HashSet<>();
            boolean coverageIncomplete = false;
            JsonNode audits = root.path("audits");
            if (audits.isArray()) {
                for (JsonNode audit : audits) {
                    String type = audit.path("type").asText("").trim().toUpperCase(Locale.ROOT);
                    String text = audit.path("text").asText("");
                    String verdict = audit.path("verdict").asText("").trim().toUpperCase(Locale.ROOT);
                    AuditItemKey key = new AuditItemKey(type, text);
                    if (type.isEmpty() || text.isEmpty() || !expected.contains(key) || !seen.add(key)) {
                        coverageIncomplete = true;
                        continue;
                    }
                    if ("UNSUPPORTED".equals(verdict) || "CONTRADICTED".equals(verdict)) {
                        unsupported.add(key);
                    } else if ("PARTIALLY_SUPPORTED".equals(verdict)) {
                        partial.add(key);
                    } else if (!"SUPPORTED".equals(verdict)) {
                        coverageIncomplete = true;
                    }
                }
            } else {
                coverageIncomplete = true;
            }
            coverageIncomplete = coverageIncomplete || !seen.containsAll(expected);

            List<DecisionCandidate> decisions = draft.decisions().stream()
                    .filter(d -> !unsupported.contains(key("DECISION", d.text())))
                    .toList();
            List<ActionItemCandidate> actions = draft.actionItems().stream()
                    .filter(a -> !unsupported.contains(key("ACTION_ITEM", a.text())))
                    .toList();
            List<RiskCandidate> risks = draft.risks().stream()
                    .filter(r -> !unsupported.contains(key("RISK", r.text())))
                    .toList();
            List<CommitmentCandidate> commitments = draft.commitments().stream()
                    .filter(c -> !unsupported.contains(key("COMMITMENT", c.text())))
                    .toList();
            List<OpenQuestionCandidate> questions = draft.openQuestions().stream()
                    .filter(q -> !unsupported.contains(key("OPEN_QUESTION", q.text())))
                    .toList();
            List<ImportantFactCandidate> facts = draft.importantFacts().stream()
                    .filter(f -> !unsupported.contains(key("IMPORTANT_FACT", f.text())))
                    .toList();
            List<TopicCandidate> topics = draft.topics().stream()
                    .filter(t -> !unsupported.contains(key("TOPIC", t.text())))
                    .toList();
            List<ProposalCandidate> proposals = draft.proposals().stream()
                    .filter(p -> !unsupported.contains(key("PROPOSAL", p.text())))
                    .toList();
            List<String> flags = new ArrayList<>(draft.qualityFlags());
            flags.add("LLM_AUDITED");
            if (!unsupported.isEmpty()) {
                flags.add("UNSUPPORTED_ITEMS_DROPPED");
            }
            if (!partial.isEmpty()) {
                flags.add("PARTIAL_EVIDENCE_NEEDS_REVIEW");
            }
            if (coverageIncomplete) {
                flags.add("AUDIT_COVERAGE_INCOMPLETE");
            }
            boolean manual = draft.requiresManualReview() || !partial.isEmpty() || coverageIncomplete;
            if (manual && !flags.contains("REQUIRES_MANUAL_REVIEW")) {
                flags.add("REQUIRES_MANUAL_REVIEW");
            }
            return successfulStep(
                    new FinalNoteDraft(
                            draft.executiveSummary(),
                            decisions,
                            actions,
                            risks,
                            questions,
                            commitments,
                            topics,
                            draft.issues(),
                            proposals,
                            facts,
                            flags,
                            draft.evidenceSegmentIds(),
                            draft.confidence(),
                            manual
                    ),
                    response
            );
        } catch (RuntimeException | IOException ex) {
            AUDIT_FALLBACKS.incrementAndGet();
            qualityMetrics.recordFallback("audit", ex.getClass().getSimpleName());
            LOG.log(Level.SEVERE,
                    () -> "Pipeline stage failed; fallback activated. stage=AUDIT reason="
                            + ex.getClass().getSimpleName());
            LOG.log(Level.SEVERE, "AUDIT fallback detail", ex);
            List<String> flags = new ArrayList<>(draft.qualityFlags());
            flags.add("AUDIT_FALLBACK");
            if (!flags.contains("AUDIT_COVERAGE_INCOMPLETE")) {
                flags.add("AUDIT_COVERAGE_INCOMPLETE");
            }
            if (!flags.contains("REQUIRES_MANUAL_REVIEW")) {
                flags.add("REQUIRES_MANUAL_REVIEW");
            }
            return fallbackStep(
                    new FinalNoteDraft(
                            draft.executiveSummary(),
                            draft.decisions(),
                            draft.actionItems(),
                            draft.risks(),
                            draft.openQuestions(),
                            draft.commitments(),
                            draft.topics(),
                            draft.issues(),
                            draft.proposals(),
                            draft.importantFacts(),
                            flags,
                            draft.evidenceSegmentIds(),
                            draft.confidence(),
                            true
                    ),
                    response,
                    inferenceAttempted
            );
        }
    }

    private static Set<AuditItemKey> expectedAuditItems(FinalNoteDraft draft) {
        Set<AuditItemKey> expected = new HashSet<>();
        draft.decisions().forEach(item -> expected.add(key("DECISION", item.text())));
        draft.actionItems().forEach(item -> expected.add(key("ACTION_ITEM", item.text())));
        draft.risks().forEach(item -> expected.add(key("RISK", item.text())));
        draft.commitments().forEach(item -> expected.add(key("COMMITMENT", item.text())));
        draft.openQuestions().forEach(item -> expected.add(key("OPEN_QUESTION", item.text())));
        draft.topics().forEach(item -> expected.add(key("TOPIC", item.text())));
        draft.proposals().forEach(item -> expected.add(key("PROPOSAL", item.text())));
        draft.importantFacts().forEach(item -> expected.add(key("IMPORTANT_FACT", item.text())));
        return expected;
    }

    private static AuditItemKey key(String type, String text) {
        return new AuditItemKey(type.toUpperCase(Locale.ROOT), text == null ? "" : text);
    }

    private static StepResult successfulStep(FinalNoteDraft draft, InferenceResponse response) {
        return new StepResult(
                draft,
                1,
                response.inputTokens(),
                response.outputTokens(),
                response.latencyMs(),
                false
        );
    }

    private static StepResult fallbackStep(
            FinalNoteDraft draft,
            InferenceResponse response,
            boolean inferenceAttempted
    ) {
        return new StepResult(
                draft,
                inferenceAttempted ? 1 : 0,
                response == null ? 0 : response.inputTokens(),
                response == null ? 0 : response.outputTokens(),
                response == null ? 0 : response.latencyMs(),
                true
        );
    }

    private record StepResult(
            FinalNoteDraft draft,
            int modelCalls,
            long inputTokens,
            long outputTokens,
            long modelLatencyMs,
            boolean fallbackUsed
    ) {
    }

    private record AuditItemKey(String type, String text) {
    }

    /** Test/ops visibility for fallback counters. */
    public static long synthesisFallbackCount() {
        return SYNTHESIS_FALLBACKS.get();
    }

    public static long auditFallbackCount() {
        return AUDIT_FALLBACKS.get();
    }

    private JsonNode parseSynthesisJson(String raw) {
        String json = raw == null ? "" : raw.trim();
        try {
            if (jsonRepair.needsRepair(json)) {
                json = jsonRepair.repairOrThrow(json);
            }
            return schemaValidator.parseAndValidate(json);
        } catch (RuntimeException first) {
            String repaired = jsonRepair.repairOrThrow(raw == null ? "" : raw);
            return schemaValidator.parseAndValidate(repaired);
        }
    }

    private JsonNode parseAuditJson(String raw) throws IOException {
        String json = raw == null ? "" : raw.trim();
        try {
            if (jsonRepair.needsRepair(json)) {
                json = jsonRepair.repairOrThrow(json);
            }
            return objectMapper.readTree(json);
        } catch (RuntimeException | IOException first) {
            String repaired = jsonRepair.repairOrThrow(raw == null ? "" : raw);
            return objectMapper.readTree(repaired);
        }
    }

    private static <T> List<T> preferNonEmpty(List<T> primary, List<T> fallback) {
        if (primary != null && !primary.isEmpty()) {
            return primary;
        }
        return fallback == null ? List.of() : fallback;
    }

    /**
     * Union primary (synthesis) with fallback (pre-synthesis candidates).
     * Dedup by normalized text; prefer the higher-confidence / richer-evidence variant.
     * Prevents open-question recall collapse when the final-minutes model returns a subset.
     */
    static List<OpenQuestionCandidate> unionOpenQuestions(
            List<OpenQuestionCandidate> primary,
            List<OpenQuestionCandidate> fallback
    ) {
        List<OpenQuestionCandidate> a = primary == null ? List.of() : primary;
        List<OpenQuestionCandidate> b = fallback == null ? List.of() : fallback;
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        Map<String, OpenQuestionCandidate> byKey = new LinkedHashMap<>();
        for (OpenQuestionCandidate q : a) {
            if (q == null || q.text() == null || q.text().isBlank()) {
                continue;
            }
            byKey.put(normalizeQuestionKey(q.text()), q);
        }
        for (OpenQuestionCandidate q : b) {
            if (q == null || q.text() == null || q.text().isBlank()) {
                continue;
            }
            String key = normalizeQuestionKey(q.text());
            OpenQuestionCandidate existing = byKey.get(key);
            if (existing == null) {
                byKey.put(key, q);
                continue;
            }
            byKey.put(key, preferRicherQuestion(existing, q));
        }
        return List.copyOf(byKey.values());
    }

    private static String normalizeQuestionKey(String text) {
        return text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static OpenQuestionCandidate preferRicherQuestion(
            OpenQuestionCandidate left,
            OpenQuestionCandidate right
    ) {
        int leftEvidence = left.evidenceSegmentIds() == null ? 0 : left.evidenceSegmentIds().size();
        int rightEvidence = right.evidenceSegmentIds() == null ? 0 : right.evidenceSegmentIds().size();
        if (rightEvidence > leftEvidence) {
            return right;
        }
        if (rightEvidence < leftEvidence) {
            return left;
        }
        return right.confidence() > left.confidence() ? right : left;
    }

    /**
     * Union synthesis important facts with pre-synthesis candidates so measured
     * observations (error rates, client counts) survive partial LLM final-minutes output.
     */
    static List<ImportantFactCandidate> unionImportantFacts(
            List<ImportantFactCandidate> primary,
            List<ImportantFactCandidate> fallback
    ) {
        List<ImportantFactCandidate> a = primary == null ? List.of() : primary;
        List<ImportantFactCandidate> b = fallback == null ? List.of() : fallback;
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        Map<String, ImportantFactCandidate> byKey = new LinkedHashMap<>();
        for (ImportantFactCandidate f : a) {
            if (f == null || f.text() == null || f.text().isBlank()) {
                continue;
            }
            byKey.put(normalizeQuestionKey(f.text()), f);
        }
        for (ImportantFactCandidate f : b) {
            if (f == null || f.text() == null || f.text().isBlank()) {
                continue;
            }
            String key = normalizeQuestionKey(f.text());
            ImportantFactCandidate existing = byKey.get(key);
            if (existing == null) {
                byKey.put(key, f);
                continue;
            }
            byKey.put(key, preferRicherFact(existing, f));
        }
        return List.copyOf(byKey.values());
    }

    private static ImportantFactCandidate preferRicherFact(
            ImportantFactCandidate left,
            ImportantFactCandidate right
    ) {
        int leftEvidence = left.evidenceSegmentIds() == null ? 0 : left.evidenceSegmentIds().size();
        int rightEvidence = right.evidenceSegmentIds() == null ? 0 : right.evidenceSegmentIds().size();
        if (rightEvidence > leftEvidence) {
            return right;
        }
        if (rightEvidence < leftEvidence) {
            return left;
        }
        return right.confidence() > left.confidence() ? right : left;
    }

    /**
     * Prefer synthesis actions when present, but restore relativeDate/dueAt/dueDate from
     * deterministic candidates when the model drops structured date fields.
     */
    private static List<ActionItemCandidate> preferActionsPreserveDates(
            List<ActionItemCandidate> primary,
            List<ActionItemCandidate> fallback
    ) {
        if (primary == null || primary.isEmpty()) {
            return fallback == null ? List.of() : fallback;
        }
        if (fallback == null || fallback.isEmpty()) {
            return primary;
        }
        List<ActionItemCandidate> out = new ArrayList<>();
        for (ActionItemCandidate item : primary) {
            ActionItemCandidate donor = findDateDonor(item, fallback);
            if (donor == null) {
                out.add(item);
                continue;
            }
            String relative = blankToNull(item.relativeDate()) != null ? item.relativeDate() : donor.relativeDate();
            String dueAt = blankToNull(item.dueAt()) != null ? item.dueAt() : donor.dueAt();
            String dueDate = blankToNull(item.dueDate()) != null ? item.dueDate() : donor.dueDate();
            String owner = blankToNull(item.owner()) != null ? item.owner() : donor.owner();
            out.add(new ActionItemCandidate(
                    item.text(),
                    owner,
                    dueDate,
                    item.evidenceSegmentIds(),
                    item.confidence(),
                    blankToNull(item.ownerType()) != null ? item.ownerType() : donor.ownerType(),
                    blankToNull(item.priority()) != null ? item.priority() : donor.priority(),
                    relative,
                    dueAt
            ));
        }
        return out;
    }

    private static ActionItemCandidate findDateDonor(
            ActionItemCandidate item,
            List<ActionItemCandidate> fallback
    ) {
        String owner = item.owner() == null ? "" : item.owner().toLowerCase(Locale.ROOT);
        String core = item.text() == null ? "" : item.text().toLowerCase(Locale.ROOT);
        ActionItemCandidate best = null;
        for (ActionItemCandidate f : fallback) {
            String fo = f.owner() == null ? "" : f.owner().toLowerCase(Locale.ROOT);
            if (!owner.isBlank() && !fo.equals(owner)) {
                continue;
            }
            if (blankToNull(f.relativeDate()) == null && blankToNull(f.dueAt()) == null && blankToNull(f.dueDate()) == null) {
                continue;
            }
            String fc = f.text() == null ? "" : f.text().toLowerCase(Locale.ROOT);
            if (core.contains(fc) || fc.contains(core) || (!owner.isBlank() && fo.equals(owner))) {
                best = f;
                if (!owner.isBlank() && fo.equals(owner)) {
                    return f;
                }
            }
        }
        return best;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private ObjectNode toCandidateNode(ExtractionBundle bundle) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode topics = root.putArray("topics");
        for (TopicCandidate topic : bundle.topics()) {
            ObjectNode n = topics.addObject();
            n.put("text", topic.text());
            if (topic.summary() == null) {
                n.putNull("summary");
            } else {
                n.put("summary", topic.summary());
            }
            ArrayNode ev = n.putArray("evidenceSegmentIds");
            topic.evidenceSegmentIds().forEach(ev::add);
            n.put("confidence", topic.confidence());
        }
        root.set("decisions", texts(bundle.decisions().stream().map(DecisionCandidate::text).toList(),
                bundle.decisions().stream().map(DecisionCandidate::evidenceSegmentIds).toList(),
                bundle.decisions().stream().map(DecisionCandidate::confidence).toList()));
        ArrayNode actions = root.putArray("actionItems");
        for (ActionItemCandidate item : bundle.actionItems()) {
            ObjectNode n = actions.addObject();
            n.put("text", item.text());
            if (item.owner() == null) {
                n.putNull("owner");
            } else {
                n.put("owner", item.owner());
            }
            if (item.ownerType() == null) {
                n.putNull("ownerType");
            } else {
                n.put("ownerType", item.ownerType());
            }
            if (item.dueDate() == null) {
                n.putNull("dueDate");
            } else {
                n.put("dueDate", item.dueDate());
            }
            if (item.relativeDate() == null) {
                n.putNull("relativeDate");
            } else {
                n.put("relativeDate", item.relativeDate());
            }
            if (item.dueAt() == null) {
                n.putNull("dueAt");
            } else {
                n.put("dueAt", item.dueAt());
            }
            if (item.priority() == null) {
                n.putNull("priority");
            } else {
                n.put("priority", item.priority());
            }
            ArrayNode ev = n.putArray("evidenceSegmentIds");
            item.evidenceSegmentIds().forEach(ev::add);
            n.put("confidence", item.confidence());
        }
        ArrayNode risks = root.putArray("risks");
        for (RiskCandidate risk : bundle.risks()) {
            ObjectNode n = risks.addObject();
            n.put("text", risk.text());
            if (risk.likelihood() == null) {
                n.putNull("likelihood");
            } else {
                n.put("likelihood", risk.likelihood());
            }
            if (risk.mitigation() == null) {
                n.putNull("mitigation");
            } else {
                n.put("mitigation", risk.mitigation());
            }
            ArrayNode ev = n.putArray("evidenceSegmentIds");
            risk.evidenceSegmentIds().forEach(ev::add);
            n.put("confidence", risk.confidence());
        }
        root.set("openQuestions", texts(bundle.openQuestions().stream().map(OpenQuestionCandidate::text).toList(),
                bundle.openQuestions().stream().map(OpenQuestionCandidate::evidenceSegmentIds).toList(),
                bundle.openQuestions().stream().map(OpenQuestionCandidate::confidence).toList()));
        ArrayNode commitments = root.putArray("commitments");
        for (CommitmentCandidate item : bundle.commitments()) {
            ObjectNode n = commitments.addObject();
            n.put("text", item.text());
            if (item.owner() == null) {
                n.putNull("owner");
            } else {
                n.put("owner", item.owner());
            }
            ArrayNode ev = n.putArray("evidenceSegmentIds");
            item.evidenceSegmentIds().forEach(ev::add);
            n.put("confidence", item.confidence());
        }
        root.set("issues", texts(bundle.issues().stream().map(IssueCandidate::text).toList(),
                bundle.issues().stream().map(IssueCandidate::evidenceSegmentIds).toList(),
                bundle.issues().stream().map(IssueCandidate::confidence).toList()));
        root.set("proposals", texts(bundle.proposals().stream().map(ProposalCandidate::text).toList(),
                bundle.proposals().stream().map(ProposalCandidate::evidenceSegmentIds).toList(),
                bundle.proposals().stream().map(ProposalCandidate::confidence).toList()));
        root.set("importantFacts", texts(
                bundle.importantFacts().stream().map(ImportantFactCandidate::text).toList(),
                bundle.importantFacts().stream().map(ImportantFactCandidate::evidenceSegmentIds).toList(),
                bundle.importantFacts().stream().map(ImportantFactCandidate::confidence).toList()));
        ArrayNode flags = root.putArray("qualityFlags");
        bundle.qualityFlags().forEach(flags::add);
        ArrayNode evidence = root.putArray("evidenceSegmentIds");
        bundle.evidenceSegmentIds().forEach(evidence::add);
        root.put("confidence", bundle.confidence());
        return root;
    }

    private ObjectNode toDraftNode(FinalNoteDraft draft) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("executiveSummary", draft.executiveSummary());
        root.set("decisions", texts(draft.decisions().stream().map(DecisionCandidate::text).toList(),
                draft.decisions().stream().map(DecisionCandidate::evidenceSegmentIds).toList(),
                draft.decisions().stream().map(DecisionCandidate::confidence).toList()));
        ArrayNode actions = root.putArray("actionItems");
        for (ActionItemCandidate item : draft.actionItems()) {
            ObjectNode n = actions.addObject();
            n.put("text", item.text());
            ArrayNode ev = n.putArray("evidenceSegmentIds");
            item.evidenceSegmentIds().forEach(ev::add);
        }
        ArrayNode risks = root.putArray("risks");
        for (RiskCandidate risk : draft.risks()) {
            ObjectNode n = risks.addObject();
            n.put("text", risk.text());
            if (risk.likelihood() == null) {
                n.putNull("likelihood");
            } else {
                n.put("likelihood", risk.likelihood());
            }
            if (risk.mitigation() == null) {
                n.putNull("mitigation");
            } else {
                n.put("mitigation", risk.mitigation());
            }
            ArrayNode ev = n.putArray("evidenceSegmentIds");
            risk.evidenceSegmentIds().forEach(ev::add);
            n.put("confidence", risk.confidence());
        }
        root.set("openQuestions", texts(draft.openQuestions().stream().map(OpenQuestionCandidate::text).toList(),
                draft.openQuestions().stream().map(OpenQuestionCandidate::evidenceSegmentIds).toList(),
                draft.openQuestions().stream().map(OpenQuestionCandidate::confidence).toList()));
        ArrayNode commitments = root.putArray("commitments");
        for (CommitmentCandidate item : draft.commitments()) {
            ObjectNode n = commitments.addObject();
            n.put("text", item.text());
            ArrayNode ev = n.putArray("evidenceSegmentIds");
            item.evidenceSegmentIds().forEach(ev::add);
        }
        ArrayNode topics = root.putArray("topics");
        for (TopicCandidate item : draft.topics()) {
            ObjectNode n = topics.addObject();
            n.put("text", item.text());
            ArrayNode ev = n.putArray("evidenceSegmentIds");
            item.evidenceSegmentIds().forEach(ev::add);
        }
        root.set("proposals", texts(draft.proposals().stream().map(ProposalCandidate::text).toList(),
                draft.proposals().stream().map(ProposalCandidate::evidenceSegmentIds).toList(),
                draft.proposals().stream().map(ProposalCandidate::confidence).toList()));
        root.set("importantFacts", texts(
                draft.importantFacts().stream().map(ImportantFactCandidate::text).toList(),
                draft.importantFacts().stream().map(ImportantFactCandidate::evidenceSegmentIds).toList(),
                draft.importantFacts().stream().map(ImportantFactCandidate::confidence).toList()));
        return root;
    }

    private ArrayNode texts(List<String> texts, List<List<String>> evidence, List<Double> confidences) {
        ArrayNode array = objectMapper.createArrayNode();
        for (int i = 0; i < texts.size(); i++) {
            ObjectNode n = array.addObject();
            n.put("text", texts.get(i));
            ArrayNode ev = n.putArray("evidenceSegmentIds");
            if (i < evidence.size()) {
                evidence.get(i).forEach(ev::add);
            }
            n.put("confidence", i < confidences.size() ? confidences.get(i) : 0.7d);
        }
        return array;
    }

    private static ExtractionBundle stripUnknownEvidence(ExtractionBundle bundle, Set<String> allowed) {
        return new EvidenceReferenceScrubber(EvidenceNearMissConfig.disabled())
                .scrub(bundle, allowed)
                .bundle();
    }

    private static FinalNoteDraft scrubDraftEvidence(FinalNoteDraft draft, Set<String> allowed) {
        ExtractionBundle scrubbed = stripUnknownEvidence(new ExtractionBundle(
                draft.topics(),
                draft.decisions(),
                draft.actionItems(),
                draft.risks(),
                draft.openQuestions(),
                draft.commitments(),
                draft.issues(),
                draft.proposals(),
                draft.importantFacts(),
                draft.qualityFlags(),
                draft.evidenceSegmentIds(),
                draft.confidence()
        ), allowed);
        return new FinalNoteDraft(
                draft.executiveSummary(),
                scrubbed.decisions(),
                scrubbed.actionItems(),
                scrubbed.risks(),
                scrubbed.openQuestions(),
                scrubbed.commitments(),
                scrubbed.topics(),
                scrubbed.issues(),
                scrubbed.proposals(),
                scrubbed.importantFacts(),
                scrubbed.qualityFlags(),
                scrubbed.evidenceSegmentIds().isEmpty() ? draft.evidenceSegmentIds() : scrubbed.evidenceSegmentIds(),
                scrubbed.confidence(),
                draft.requiresManualReview()
        );
    }

    private static String textOr(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String loadTemplate(String classpath) {
        try (InputStream in = MinutesSynthesisAndAudit.class.getResourceAsStream(classpath)) {
            if (in == null) {
                throw new IllegalStateException("Configured prompt resource was not found: " + classpath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load template " + classpath, ex);
        }
    }
}
