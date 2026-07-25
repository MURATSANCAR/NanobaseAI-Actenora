package com.nanobaseai.actenora.aiprocessing.application.modelworker;

import java.util.Objects;

/**
 * Resolved prompt material for a single inference call.
 * Must never be written to structured logs or metrics labels.
 */
public record ResolvedInferenceInput(
        String systemPrompt,
        String userPrompt
) {
    public ResolvedInferenceInput {
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
        Objects.requireNonNull(userPrompt, "userPrompt");
    }

    public static ResolvedInferenceInput of(String systemPrompt, String userPrompt) {
        return new ResolvedInferenceInput(systemPrompt, userPrompt);
    }
}
