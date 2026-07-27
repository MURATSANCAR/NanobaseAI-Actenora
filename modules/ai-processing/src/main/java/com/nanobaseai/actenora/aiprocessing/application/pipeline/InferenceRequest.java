package com.nanobaseai.actenora.aiprocessing.application.pipeline;

import java.util.List;
import java.util.Objects;

public record InferenceRequest(
        String taskType,
        String promptVersionId,
        String schemaVersion,
        String systemPrompt,
        String userPrompt,
        List<String> allowedEvidenceSegmentIds,
        int maxOutputTokens,
        int timeoutSeconds
) {
    public InferenceRequest(
            String taskType,
            String promptVersionId,
            String schemaVersion,
            String systemPrompt,
            String userPrompt,
            List<String> allowedEvidenceSegmentIds,
            int maxOutputTokens
    ) {
        this(
                taskType,
                promptVersionId,
                schemaVersion,
                systemPrompt,
                userPrompt,
                allowedEvidenceSegmentIds,
                maxOutputTokens,
                0
        );
    }

    public InferenceRequest {
        Objects.requireNonNull(taskType, "taskType");
        Objects.requireNonNull(promptVersionId, "promptVersionId");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(userPrompt, "userPrompt");
        allowedEvidenceSegmentIds = List.copyOf(
                Objects.requireNonNull(allowedEvidenceSegmentIds, "allowedEvidenceSegmentIds")
        );
        if (timeoutSeconds < 0) {
            throw new IllegalArgumentException("timeoutSeconds must be >= 0");
        }
    }
}
