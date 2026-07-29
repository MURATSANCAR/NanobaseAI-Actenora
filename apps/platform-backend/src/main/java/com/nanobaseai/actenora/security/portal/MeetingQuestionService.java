package com.nanobaseai.actenora.security.portal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.InferenceRequest;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.InferenceResponse;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelRuntimePort;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import com.nanobaseai.actenora.transcript.api.dto.TranscriptSegmentView;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Evidence-constrained meeting Q&A. Prompt behavior is supplied by a versioned resource.
 */
@Component
public final class MeetingQuestionService {

    private final ModelRuntimePort runtime;
    private final ObjectMapper objectMapper;
    private final String systemPrompt;
    private final String taskType;
    private final String promptVersion;
    private final String schemaVersion;
    private final int maxOutputTokens;
    private final int timeoutSeconds;

    public MeetingQuestionService(
            ObjectProvider<ModelRuntimePort> runtime,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader,
            @Value("${actenora.portal.meeting-question.prompt-resource}") String promptResource,
            @Value("${actenora.portal.meeting-question.task-type}") String taskType,
            @Value("${actenora.portal.meeting-question.prompt-version}") String promptVersion,
            @Value("${actenora.portal.meeting-question.schema-version}") String schemaVersion,
            @Value("${actenora.portal.meeting-question.max-output-tokens}") int maxOutputTokens,
            @Value("${actenora.portal.meeting-question.timeout-seconds}") int timeoutSeconds
    ) {
        this.runtime = runtime.getIfAvailable();
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.systemPrompt = readResource(resourceLoader.getResource(promptResource));
        this.taskType = requireText(taskType, "taskType");
        this.promptVersion = requireText(promptVersion, "promptVersion");
        this.schemaVersion = requireText(schemaVersion, "schemaVersion");
        if (maxOutputTokens < 1 || timeoutSeconds < 1) {
            throw new IllegalArgumentException("Meeting question inference limits must be positive");
        }
        this.maxOutputTokens = maxOutputTokens;
        this.timeoutSeconds = timeoutSeconds;
    }

    public Answer answer(String question, List<TranscriptSegmentView> evidence) {
        String normalizedQuestion = requireText(question, "question");
        List<TranscriptSegmentView> safeEvidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        if (safeEvidence.isEmpty()) {
            return Answer.insufficient(null, 0, 0);
        }
        if (runtime == null || !runtime.healthy()) {
            throw new ActenoraException(
                    "MEETING_QUESTION_RUNTIME_UNAVAILABLE",
                    "Meeting question model runtime is unavailable");
        }

        ObjectNode input = objectMapper.createObjectNode();
        input.put("question", normalizedQuestion);
        ArrayNode evidenceNode = input.putArray("evidence");
        Set<String> allowed = new LinkedHashSet<>();
        for (TranscriptSegmentView segment : safeEvidence) {
            String id = segment.id().toString();
            allowed.add(id);
            ObjectNode node = evidenceNode.addObject();
            node.put("segmentId", id);
            node.put("speaker", segment.speaker());
            node.put("startMs", segment.startMs());
            node.put("endMs", segment.endMs());
            node.put("text", segment.text());
        }

        InferenceResponse response = runtime.infer(new InferenceRequest(
                taskType,
                promptVersion,
                schemaVersion,
                systemPrompt,
                input.toString(),
                List.copyOf(allowed),
                maxOutputTokens,
                timeoutSeconds
        ));
        return parse(response, allowed);
    }

    private Answer parse(InferenceResponse response, Set<String> allowed) {
        try {
            JsonNode root = objectMapper.readTree(stripFence(response.rawText()));
            String status = root.path("status").asText("");
            if ("INSUFFICIENT_EVIDENCE".equals(status)) {
                return Answer.insufficient(
                        response.modelVersion(),
                        response.inputTokens(),
                        response.outputTokens());
            }
            if (!"ANSWERED".equals(status)) {
                throw invalidResponse();
            }
            String answer = root.path("answer").asText("").trim();
            LinkedHashSet<String> citations = new LinkedHashSet<>();
            JsonNode citationNode = root.path("citations");
            if (citationNode.isArray()) {
                citationNode.forEach(node -> {
                    String id = node.asText("");
                    if (allowed.contains(id)) {
                        citations.add(id);
                    }
                });
            }
            if (answer.isBlank() || citations.isEmpty()) {
                return Answer.insufficient(
                        response.modelVersion(),
                        response.inputTokens(),
                        response.outputTokens());
            }
            return new Answer(
                    "ANSWERED",
                    answer,
                    List.copyOf(citations),
                    response.modelVersion(),
                    response.inputTokens(),
                    response.outputTokens()
            );
        } catch (ActenoraException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalidResponse();
        }
    }

    private static ActenoraException invalidResponse() {
        return new ActenoraException(
                "MEETING_QUESTION_INVALID_RESPONSE",
                "Meeting question model returned an invalid evidence response");
    }

    private static String stripFence(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.startsWith("```")) {
            return value;
        }
        int firstBreak = value.indexOf('\n');
        int lastFence = value.lastIndexOf("```");
        return firstBreak >= 0 && lastFence > firstBreak
                ? value.substring(firstBreak + 1, lastFence).trim()
                : value;
    }

    private static String readResource(Resource resource) {
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Meeting question prompt resource could not be loaded", ex);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public record Answer(
            String status,
            String text,
            List<String> citationSegmentIds,
            String modelVersion,
            long inputTokens,
            long outputTokens
    ) {
        static Answer insufficient(String modelVersion, long inputTokens, long outputTokens) {
            return new Answer(
                    "INSUFFICIENT_EVIDENCE",
                    null,
                    List.of(),
                    modelVersion,
                    inputTokens,
                    outputTokens);
        }
    }
}
