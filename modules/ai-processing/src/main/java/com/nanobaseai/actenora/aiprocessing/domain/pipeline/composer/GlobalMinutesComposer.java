package com.nanobaseai.actenora.aiprocessing.domain.pipeline.composer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.InferenceRequest;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.InferenceResponse;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelRuntimePort;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.MeetingLlmBudgets;
import com.nanobaseai.actenora.aiprocessing.domain.routing.InferenceTaskType;
import com.nanobaseai.actenora.aiprocessing.infrastructure.json.LimitedJsonRepair;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Global candidate composer: understands the meeting via digest + ledger hints.
 * Emits meetingFrame + candidates only — never final user-facing minutes prose.
 */
public final class GlobalMinutesComposer {

    public static final String PROMPT_RESOURCE = "/aiprocessing/prompts/global-candidate-composer.v1.txt";
    public static final String PROMPT_VERSION = "pv-meeting-global-composer-v1";
    public static final String SCHEMA_VERSION = "meeting.global-composition.v1";

    private final ModelRuntimePort modelRuntime;
    private final ObjectMapper objectMapper;
    private final LimitedJsonRepair jsonRepair;
    private final String promptTemplate;
    private final int timeoutSeconds;

    public GlobalMinutesComposer(ModelRuntimePort modelRuntime, int timeoutSeconds) {
        this(modelRuntime, new ObjectMapper(), new LimitedJsonRepair(), loadPrompt(), timeoutSeconds);
    }

    GlobalMinutesComposer(
            ModelRuntimePort modelRuntime,
            ObjectMapper objectMapper,
            LimitedJsonRepair jsonRepair,
            String promptTemplate,
            int timeoutSeconds
    ) {
        this.modelRuntime = Objects.requireNonNull(modelRuntime, "modelRuntime");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.jsonRepair = Objects.requireNonNull(jsonRepair, "jsonRepair");
        this.promptTemplate = Objects.requireNonNull(promptTemplate, "promptTemplate");
        this.timeoutSeconds = Math.max(0, timeoutSeconds);
    }

    /**
     * Prefer LLM composition; on failure, seed candidates deterministically from digest
     * (still evidence-addressable). Caller audits independently.
     */
    public GlobalComposition compose(TranscriptDigest digest, ExtractionBundle groundedLedger) {
        Objects.requireNonNull(digest, "digest");
        Objects.requireNonNull(groundedLedger, "groundedLedger");
        try {
            return composeWithModel(digest, groundedLedger);
        } catch (RuntimeException | IOException ex) {
            return seedFromDigest(digest);
        }
    }

    public GlobalComposition seedFromDigest(TranscriptDigest digest) {
        Objects.requireNonNull(digest, "digest");
        List<GlobalComposition.GlobalCandidate> candidates = new ArrayList<>();
        for (TranscriptDigest.DigestFact fact : digest.candidateFacts()) {
            if (TranscriptDigestBuilder.KIND_SELECTION_CONFIRMATION.equals(fact.kind())) {
                candidates.add(new GlobalComposition.GlobalCandidate(
                        GlobalComposition.CandidateType.DECISION,
                        fact.text(),
                        null,
                        null,
                        null,
                        fact.evidenceSegmentIds(),
                        "DIGEST",
                        0.85d
                ));
            } else if (TranscriptDigestBuilder.KIND_FUTURE_COMMITMENT.equals(fact.kind())) {
                candidates.add(new GlobalComposition.GlobalCandidate(
                        GlobalComposition.CandidateType.ACTION,
                        fact.text(),
                        fact.speaker(),
                        fact.temporalExpression(),
                        null,
                        fact.evidenceSegmentIds(),
                        "DIGEST",
                        0.80d
                ));
            }
        }
        for (TranscriptDigest.DigestFact q : digest.unresolvedQuestions()) {
            candidates.add(new GlobalComposition.GlobalCandidate(
                    GlobalComposition.CandidateType.OPEN_QUESTION,
                    q.text(),
                    null,
                    null,
                    null,
                    q.evidenceSegmentIds(),
                    "DIGEST",
                    0.70d
            ));
        }
        GlobalComposition.MeetingFrame frame = null;
        for (TranscriptDigest.DigestSignal signal : digest.meetingSignals()) {
            if (TranscriptDigestBuilder.KIND_MEETING_CHARACTER.equals(signal.kind())) {
                frame = new GlobalComposition.MeetingFrame(
                        "INTRODUCTION_AND_EXPLORATION",
                        signal.text(),
                        signal.evidenceSegmentIds(),
                        0.80d
                );
                break;
            }
        }
        if (frame == null && !digest.meetingSignals().isEmpty()) {
            TranscriptDigest.DigestSignal s = digest.meetingSignals().getFirst();
            frame = new GlobalComposition.MeetingFrame(
                    s.kind(), s.text(), s.evidenceSegmentIds(), 0.70d);
        }
        return new GlobalComposition(frame, candidates);
    }

    private GlobalComposition composeWithModel(TranscriptDigest digest, ExtractionBundle ledger)
            throws IOException {
        ObjectNode user = objectMapper.createObjectNode();
        user.set("digest", objectMapper.valueToTree(digest));
        user.set("ledgerHints", objectMapper.valueToTree(ledger));
        InferenceResponse response = modelRuntime.infer(new InferenceRequest(
                InferenceTaskType.FINAL_NOTE.name(),
                PROMPT_VERSION,
                SCHEMA_VERSION,
                promptTemplate,
                objectMapper.writeValueAsString(user),
                List.of(),
                MeetingLlmBudgets.FINAL_MAX_TOKENS,
                timeoutSeconds
        ));
        String raw = response.rawText() == null ? "" : response.rawText();
        String repaired = jsonRepair.repairOrThrow(raw);
        JsonNode root = objectMapper.readTree(repaired);
        return parseComposition(root);
    }

    GlobalComposition parseComposition(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("COMPOSER_SCHEMA_INVALID");
        }
        JsonNode frameNode = root.path("meetingFrame");
        GlobalComposition.MeetingFrame frame = null;
        if (frameNode.isObject() && frameNode.path("text").isTextual()) {
            frame = new GlobalComposition.MeetingFrame(
                    textOr(frameNode.path("kind"), "UNKNOWN"),
                    frameNode.path("text").asText(),
                    readIds(frameNode.path("evidenceSegmentIds")),
                    frameNode.path("confidence").asDouble(0.75d)
            );
        }
        List<GlobalComposition.GlobalCandidate> candidates = new ArrayList<>();
        JsonNode arr = root.path("candidates");
        if (arr.isArray()) {
            for (JsonNode n : arr) {
                if (!n.isObject() || !n.path("text").isTextual()) {
                    continue;
                }
                candidates.add(new GlobalComposition.GlobalCandidate(
                        GlobalComposition.CandidateType.parse(textOr(n.path("type"), "ACTION")),
                        n.path("text").asText(),
                        textOrNull(n.path("ownerCandidate")),
                        textOrNull(n.path("dueDateText")),
                        textOrNull(n.path("dueDateNormalized")),
                        textOrNull(n.path("mitigation")),
                        readIds(n.path("evidenceSegmentIds")),
                        textOr(n.path("source"), "DIGEST"),
                        n.path("confidence").asDouble(0.75d)
                ));
            }
        }
        return new GlobalComposition(frame, candidates);
    }

    private static List<String> readIds(JsonNode node) {
        List<String> ids = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode n : node) {
                if (n.isIntegralNumber()) {
                    ids.add(String.valueOf(n.asInt()));
                } else if (n.isTextual() && !n.asText().isBlank()) {
                    ids.add(n.asText().strip());
                }
            }
        }
        return ids;
    }

    private static String textOr(JsonNode node, String fallback) {
        return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText().strip() : fallback;
    }

    private static String textOrNull(JsonNode node) {
        return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText().strip() : null;
    }

    private static String loadPrompt() {
        try (InputStream in = GlobalMinutesComposer.class.getResourceAsStream(PROMPT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing prompt " + PROMPT_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load " + PROMPT_RESOURCE, ex);
        }
    }
}
