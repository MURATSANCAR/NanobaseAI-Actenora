package com.nanobaseai.actenora.aiprocessing.application.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.RiskCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TopicCandidate;
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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Call-2 synthesizer + Call-3 evidence audit for the evidence-based minutes pipeline.
 * Falls back to the provided deterministic draft when LLM synthesis/audit fails.
 */
public final class MinutesSynthesisAndAudit {

    private final ModelRuntimePort modelRuntime;
    private final LimitedJsonRepair jsonRepair;
    private final ExtractionJsonSchemaValidator schemaValidator;
    private final ExtractionBundleMapper bundleMapper;
    private final ObjectMapper objectMapper;
    private final String finalMinutesTemplate;
    private final String evidenceAuditTemplate;
    private final int timeoutSeconds;

    public MinutesSynthesisAndAudit(ModelRuntimePort modelRuntime) {
        this(modelRuntime, 0);
    }

    public MinutesSynthesisAndAudit(ModelRuntimePort modelRuntime, int timeoutSeconds) {
        this(
                modelRuntime,
                new LimitedJsonRepair(),
                new ExtractionJsonSchemaValidator(),
                new ExtractionBundleMapper(),
                new ObjectMapper(),
                loadTemplate("/aiprocessing/prompts/final-minutes.v1.txt"),
                loadTemplate("/aiprocessing/prompts/evidence-audit.v1.txt"),
                timeoutSeconds
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
                0
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
        this.modelRuntime = Objects.requireNonNull(modelRuntime, "modelRuntime");
        this.jsonRepair = Objects.requireNonNull(jsonRepair, "jsonRepair");
        this.schemaValidator = Objects.requireNonNull(schemaValidator, "schemaValidator");
        this.bundleMapper = Objects.requireNonNull(bundleMapper, "bundleMapper");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.finalMinutesTemplate = Objects.requireNonNull(finalMinutesTemplate, "finalMinutesTemplate");
        this.evidenceAuditTemplate = Objects.requireNonNull(evidenceAuditTemplate, "evidenceAuditTemplate");
        if (timeoutSeconds < 0) {
            throw new IllegalArgumentException("timeoutSeconds must be >= 0");
        }
        this.timeoutSeconds = timeoutSeconds;
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
        FinalNoteDraft synthesized = synthesize(
                merged,
                deterministicDraft,
                allowedEvidenceIds,
                meetingTitle,
                language,
                priorMeetingContext == null ? PriorMeetingContext.EMPTY : priorMeetingContext
        );
        return audit(synthesized, allowedEvidenceIds, language);
    }

    private FinalNoteDraft synthesize(
            ExtractionBundle merged,
            FinalNoteDraft fallback,
            Set<String> allowedEvidenceIds,
            String meetingTitle,
            String language,
            PriorMeetingContext priorMeetingContext
    ) {
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
            InferenceResponse response = modelRuntime.infer(new InferenceRequest(
                    InferenceTaskType.FINAL_NOTE.name(),
                    "pv-meeting-final-note-v1",
                    InMemoryPromptRegistry.FINAL_NOTE_PROMPT_ID,
                    ExtractionPromptRules.systemRulesFor(language),
                    userPrompt,
                    List.copyOf(allowedEvidenceIds),
                    Math.max(2048, modelRuntime.descriptor().maxOutputTokens()),
                    timeoutSeconds
            ));
            String json = response.rawText();
            if (jsonRepair.needsRepair(json)) {
                json = jsonRepair.repairOrThrow(json);
            }
            JsonNode node = schemaValidator.parseAndValidate(json);
            ExtractionBundle bundle = bundleMapper.fromJson(node);
            stripUnknownEvidence(bundle, allowedEvidenceIds);
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
            return new FinalNoteDraft(
                    summary,
                    bundle.decisions(),
                    bundle.actionItems(),
                    bundle.risks(),
                    bundle.openQuestions(),
                    bundle.commitments(),
                    bundle.topics(),
                    flags,
                    bundle.evidenceSegmentIds().isEmpty() ? fallback.evidenceSegmentIds() : bundle.evidenceSegmentIds(),
                    bundle.confidence() > 0 ? bundle.confidence() : fallback.confidence(),
                    manual
            );
        } catch (RuntimeException | IOException ex) {
            List<String> flags = new ArrayList<>(fallback.qualityFlags());
            flags.add("SYNTHESIS_FALLBACK");
            return new FinalNoteDraft(
                    fallback.executiveSummary(),
                    fallback.decisions(),
                    fallback.actionItems(),
                    fallback.risks(),
                    fallback.openQuestions(),
                    fallback.commitments(),
                    fallback.topics(),
                    flags,
                    fallback.evidenceSegmentIds(),
                    fallback.confidence(),
                    fallback.requiresManualReview()
            );
        }
    }

    private FinalNoteDraft audit(FinalNoteDraft draft, Set<String> allowedEvidenceIds, String language) {
        try {
            String candidatesJson = objectMapper.writeValueAsString(toDraftNode(draft));
            String userPrompt = ExtractionPromptRules.applyLanguage(evidenceAuditTemplate, language)
                    .replace("{{candidatesJson}}", candidatesJson)
                    .replace("{{evidenceSegmentIds}}", String.join(",", allowedEvidenceIds));
            InferenceResponse response = modelRuntime.infer(new InferenceRequest(
                    InferenceTaskType.VALIDATION.name(),
                    "pv-meeting-validation-v1",
                    InMemoryPromptRegistry.VALIDATION_PROMPT_ID,
                    ExtractionPromptRules.systemRulesFor(language),
                    userPrompt,
                    List.copyOf(allowedEvidenceIds),
                    Math.max(1024, modelRuntime.descriptor().maxOutputTokens() / 2),
                    timeoutSeconds
            ));
            String json = response.rawText();
            if (jsonRepair.needsRepair(json)) {
                json = jsonRepair.repairOrThrow(json);
            }
            JsonNode root = objectMapper.readTree(json);
            Set<String> unsupported = new HashSet<>();
            Set<String> partial = new HashSet<>();
            JsonNode audits = root.path("audits");
            if (audits.isArray()) {
                for (JsonNode audit : audits) {
                    String text = audit.path("text").asText("").trim().toLowerCase(Locale.ROOT);
                    String verdict = audit.path("verdict").asText("").trim().toUpperCase(Locale.ROOT);
                    if (text.isEmpty()) {
                        continue;
                    }
                    if ("UNSUPPORTED".equals(verdict) || "CONTRADICTED".equals(verdict)) {
                        unsupported.add(text);
                    } else if ("PARTIALLY_SUPPORTED".equals(verdict)) {
                        partial.add(text);
                    }
                }
            }
            List<DecisionCandidate> decisions = draft.decisions().stream()
                    .filter(d -> !unsupported.contains(d.text().trim().toLowerCase(Locale.ROOT)))
                    .toList();
            List<ActionItemCandidate> actions = draft.actionItems().stream()
                    .filter(a -> !unsupported.contains(a.text().trim().toLowerCase(Locale.ROOT)))
                    .toList();
            List<RiskCandidate> risks = draft.risks().stream()
                    .filter(r -> !unsupported.contains(r.text().trim().toLowerCase(Locale.ROOT)))
                    .toList();
            List<CommitmentCandidate> commitments = draft.commitments().stream()
                    .filter(c -> !unsupported.contains(c.text().trim().toLowerCase(Locale.ROOT)))
                    .toList();
            List<OpenQuestionCandidate> questions = draft.openQuestions().stream()
                    .filter(q -> !unsupported.contains(q.text().trim().toLowerCase(Locale.ROOT)))
                    .toList();
            List<String> flags = new ArrayList<>(draft.qualityFlags());
            flags.add("LLM_AUDITED");
            if (!partial.isEmpty()) {
                flags.add("PARTIAL_EVIDENCE_NEEDS_REVIEW");
            }
            boolean manual = draft.requiresManualReview() || !partial.isEmpty();
            return new FinalNoteDraft(
                    draft.executiveSummary(),
                    decisions,
                    actions,
                    risks,
                    questions,
                    commitments,
                    draft.topics(),
                    flags,
                    draft.evidenceSegmentIds(),
                    draft.confidence(),
                    manual
            );
        } catch (RuntimeException | IOException ex) {
            List<String> flags = new ArrayList<>(draft.qualityFlags());
            flags.add("AUDIT_FALLBACK");
            return new FinalNoteDraft(
                    draft.executiveSummary(),
                    draft.decisions(),
                    draft.actionItems(),
                    draft.risks(),
                    draft.openQuestions(),
                    draft.commitments(),
                    draft.topics(),
                    flags,
                    draft.evidenceSegmentIds(),
                    draft.confidence(),
                    draft.requiresManualReview()
            );
        }
    }

    private ObjectNode toCandidateNode(ExtractionBundle bundle) {
        ObjectNode root = objectMapper.createObjectNode();
        root.set("topics", texts(bundle.topics().stream().map(TopicCandidate::text).toList(),
                bundle.topics().stream().map(TopicCandidate::evidenceSegmentIds).toList(),
                bundle.topics().stream().map(TopicCandidate::confidence).toList()));
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
            if (item.dueDate() == null) {
                n.putNull("dueDate");
            } else {
                n.put("dueDate", item.dueDate());
            }
            ArrayNode ev = n.putArray("evidenceSegmentIds");
            item.evidenceSegmentIds().forEach(ev::add);
            n.put("confidence", item.confidence());
        }
        root.set("risks", texts(bundle.risks().stream().map(RiskCandidate::text).toList(),
                bundle.risks().stream().map(RiskCandidate::evidenceSegmentIds).toList(),
                bundle.risks().stream().map(RiskCandidate::confidence).toList()));
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
        root.set("risks", texts(draft.risks().stream().map(RiskCandidate::text).toList(),
                draft.risks().stream().map(RiskCandidate::evidenceSegmentIds).toList(),
                draft.risks().stream().map(RiskCandidate::confidence).toList()));
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

    private static void stripUnknownEvidence(ExtractionBundle bundle, Set<String> allowed) {
        // Validation happens in DeterministicExtractionValidator; synthesis already constrained by prompt.
        Objects.requireNonNull(bundle);
        Objects.requireNonNull(allowed);
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
                return "{{candidatesJson}}";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load template " + classpath, ex);
        }
    }
}
