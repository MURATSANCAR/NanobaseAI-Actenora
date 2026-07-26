package com.nanobaseai.actenora.aiprocessing.domain.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Hard rules embedded with every extraction / synthesis / audit prompt.
 */
public final class ExtractionPromptRules {

    public static final String SYSTEM_RULES = loadSystemRules();

    private ExtractionPromptRules() {
    }

    private static String loadSystemRules() {
        try (InputStream in = ExtractionPromptRules.class.getResourceAsStream(
                "/aiprocessing/prompts/system-meeting-analyst.v1.txt")) {
            if (in == null) {
                return """
                        You extract structured meeting facts from the transcript only.
                        Never invent facts. Respond with JSON only. Output in Turkish.
                        """;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load system prompt", ex);
        }
    }
}
