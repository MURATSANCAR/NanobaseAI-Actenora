package com.nanobaseai.actenora.aiprocessing.infrastructure.prompt;

import com.nanobaseai.actenora.aiprocessing.application.pipeline.PromptRegistryPort;
import com.nanobaseai.actenora.aiprocessing.domain.prompt.PublishedPrompt;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory prompt registry with immutable version history (thin Prompt Pack).
 * Seeded with the FAZ 14 extraction / merge / final / validation prompts.
 */
public final class InMemoryPromptRegistry implements PromptRegistryPort {

    public static final String DEFAULT_EXTRACTION_PROMPT_ID = "meeting.chunk-extraction";
    public static final String CANDIDATE_MERGE_PROMPT_ID = "meeting.candidate-merge";
    public static final String FINAL_NOTE_PROMPT_ID = "meeting.final-note";
    public static final String VALIDATION_PROMPT_ID = "meeting.validation";

    private final Map<String, PublishedPrompt> byVersionId = new ConcurrentHashMap<>();
    private final Map<String, String> latestVersionByPromptId = new ConcurrentHashMap<>();

    public InMemoryPromptRegistry() {
        seed(defaultExtractionPrompt());
        seed(new PublishedPrompt(
                "pv-meeting-candidate-merge-v1",
                CANDIDATE_MERGE_PROMPT_ID,
                1,
                "Merge candidate extractions into one deduplicated set. Keep evidence ids intact.",
                "candidate-merge.v1",
                "SUMMARIZATION"
        ));
        seed(new PublishedPrompt(
                "pv-meeting-final-note-v1",
                FINAL_NOTE_PROMPT_ID,
                1,
                "Write the final meeting note from merged candidates. Cite evidence ids only.",
                "final-note.v1",
                "FINAL_NOTE"
        ));
        seed(new PublishedPrompt(
                "pv-meeting-validation-v1",
                VALIDATION_PROMPT_ID,
                1,
                "Validate the produced note against evidence. Report unsupported claims.",
                "validation.v1",
                "VALIDATION"
        ));
    }

    /**
     * @deprecated Prefer {@link #publish(String, String, String, String)} which never overwrites history.
     */
    @Deprecated
    public void publish(PublishedPrompt prompt) {
        Objects.requireNonNull(prompt, "prompt");
        seed(prompt);
    }

    @Override
    public PublishedPrompt requirePublished(String promptId) {
        Objects.requireNonNull(promptId, "promptId");
        String versionId = latestVersionByPromptId.get(promptId);
        if (versionId == null) {
            throw new ActenoraException("PROMPT_NOT_PUBLISHED", "No published prompt for id=" + promptId);
        }
        return requireByVersionId(versionId);
    }

    @Override
    public PublishedPrompt requireByVersionId(String promptVersionId) {
        return findByVersionId(promptVersionId)
                .orElseThrow(() -> new ActenoraException(
                        "PROMPT_VERSION_NOT_FOUND", "No published prompt version id=" + promptVersionId));
    }

    @Override
    public Optional<PublishedPrompt> findByVersionId(String promptVersionId) {
        Objects.requireNonNull(promptVersionId, "promptVersionId");
        return Optional.ofNullable(byVersionId.get(promptVersionId));
    }

    @Override
    public synchronized PublishedPrompt publish(
            String promptId,
            String template,
            String outputSchemaId,
            String modelCapabilityRequired
    ) {
        Objects.requireNonNull(promptId, "promptId");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(outputSchemaId, "outputSchemaId");
        int nextVersion = listVersions(promptId).stream()
                .mapToInt(PublishedPrompt::version)
                .max()
                .orElse(0) + 1;
        String versionId = "pv-" + promptId.replace('.', '-') + "-v" + nextVersion;
        PublishedPrompt published = new PublishedPrompt(
                versionId,
                promptId,
                nextVersion,
                template,
                outputSchemaId,
                modelCapabilityRequired
        );
        seed(published);
        return published;
    }

    @Override
    public List<PublishedPrompt> listVersions(String promptId) {
        Objects.requireNonNull(promptId, "promptId");
        List<PublishedPrompt> versions = new ArrayList<>();
        for (PublishedPrompt prompt : byVersionId.values()) {
            if (prompt.promptId().equals(promptId)) {
                versions.add(prompt);
            }
        }
        versions.sort(Comparator.comparingInt(PublishedPrompt::version));
        return List.copyOf(versions);
    }

    private void seed(PublishedPrompt prompt) {
        byVersionId.put(prompt.promptVersionId(), prompt);
        String currentLatest = latestVersionByPromptId.get(prompt.promptId());
        if (currentLatest == null) {
            latestVersionByPromptId.put(prompt.promptId(), prompt.promptVersionId());
            return;
        }
        PublishedPrompt current = byVersionId.get(currentLatest);
        if (current == null || prompt.version() >= current.version()) {
            latestVersionByPromptId.put(prompt.promptId(), prompt.promptVersionId());
        }
    }

    private static PublishedPrompt defaultExtractionPrompt() {
        return new PublishedPrompt(
                "pv-meeting-chunk-extraction-v1",
                DEFAULT_EXTRACTION_PROMPT_ID,
                1,
                loadTemplate(),
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
