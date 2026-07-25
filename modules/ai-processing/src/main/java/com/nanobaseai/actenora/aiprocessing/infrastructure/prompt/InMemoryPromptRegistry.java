package com.nanobaseai.actenora.aiprocessing.infrastructure.prompt;

import com.nanobaseai.actenora.aiprocessing.application.pipeline.PromptRegistryPort;
import com.nanobaseai.actenora.aiprocessing.domain.prompt.PublishedPrompt;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory prompt registry seeded with the FAZ 14 published extraction prompt.
 */
public final class InMemoryPromptRegistry implements PromptRegistryPort {

    public static final String DEFAULT_EXTRACTION_PROMPT_ID = "meeting.chunk-extraction";

    private final Map<String, PublishedPrompt> published = new ConcurrentHashMap<>();

    public InMemoryPromptRegistry() {
        published.put(DEFAULT_EXTRACTION_PROMPT_ID, defaultExtractionPrompt());
    }

    public void publish(PublishedPrompt prompt) {
        Objects.requireNonNull(prompt, "prompt");
        published.put(prompt.promptId(), prompt);
    }

    @Override
    public PublishedPrompt requirePublished(String promptId) {
        PublishedPrompt prompt = published.get(promptId);
        if (prompt == null) {
            throw new ActenoraException("PROMPT_NOT_PUBLISHED", "No published prompt for id=" + promptId);
        }
        return prompt;
    }

    private static PublishedPrompt defaultExtractionPrompt() {
        String template = loadTemplate();
        return new PublishedPrompt(
                "pv-meeting-chunk-extraction-v1",
                DEFAULT_EXTRACTION_PROMPT_ID,
                1,
                template,
                "extraction-output.v1",
                "TRANSCRIPT_EXTRACTION"
        );
    }

    private static String loadTemplate() {
        try (InputStream in = InMemoryPromptRegistry.class.getResourceAsStream(
                "/aiprocessing/prompts/chunk-extraction.v1.txt")) {
            if (in == null) {
                return "Extract structured facts from the transcript chunk. Allowed evidence ids: {{evidenceSegmentIds}}\n\n{{chunk}}";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load prompt template", ex);
        }
    }
}
