package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Detects when model output appears to obey transcript-embedded instructions.
 */
public final class PromptInjectionGuard {

    private static final Pattern INJECTION_FOLLOWED = Pattern.compile(
            "(?i)\\b(ignore\\s+(all\\s+)?(previous|prior|system)\\s+instructions|"
                    + "system\\s*override|dan\\s*mode|jailbreak|"
                    + "önceki\\s+talimatları\\s+yoksay)\\b"
    );

    public void assertClean(String modelOutput) {
        if (modelOutput == null) {
            return;
        }
        String lowered = modelOutput.toLowerCase(Locale.ROOT);
        if (INJECTION_FOLLOWED.matcher(modelOutput).find()
                || lowered.contains("\"systemoverride\":true")
                || lowered.contains("\"ignore_rules\":true")) {
            throw new PipelineException(
                    FailureCategory.PROMPT_INJECTION,
                    PipelineStage.EXTRACT,
                    "Model output followed transcript-embedded instructions"
            );
        }
    }
}
