package com.nanobaseai.actenora.aiprocessing.infrastructure.prompt;

import com.nanobaseai.actenora.aiprocessing.application.modelworker.ResolvedInferenceInput;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.PromptRegistryPort;
import com.nanobaseai.actenora.aiprocessing.application.port.InferenceInputResolverPort;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.prompt.PublishedPrompt;
import com.nanobaseai.actenora.aiprocessing.domain.routing.InferenceTaskType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builds provider input from a published prompt plus job references.
 *
 * <p>Transcript content is not read here: the job path passes references only and the
 * FAZ 14 extraction pipeline owns chunk binding.
 */
public final class PromptRegistryInferenceInputResolver implements InferenceInputResolverPort {

    private final PromptRegistryPort promptRegistry;
    private final Map<InferenceTaskType, String> promptIds;

    public PromptRegistryInferenceInputResolver(PromptRegistryPort promptRegistry) {
        this(promptRegistry, defaultPromptIds());
    }

    public PromptRegistryInferenceInputResolver(
            PromptRegistryPort promptRegistry,
            Map<InferenceTaskType, String> promptIds
    ) {
        this.promptRegistry = Objects.requireNonNull(promptRegistry, "promptRegistry");
        this.promptIds = new EnumMap<>(Objects.requireNonNull(promptIds, "promptIds"));
    }

    public static Map<InferenceTaskType, String> defaultPromptIds() {
        Map<InferenceTaskType, String> ids = new EnumMap<>(InferenceTaskType.class);
        ids.put(InferenceTaskType.CHUNK_EXTRACTION, InMemoryPromptRegistry.DEFAULT_EXTRACTION_PROMPT_ID);
        ids.put(InferenceTaskType.CANDIDATE_MERGE, InMemoryPromptRegistry.CANDIDATE_MERGE_PROMPT_ID);
        ids.put(InferenceTaskType.FINAL_NOTE, InMemoryPromptRegistry.FINAL_NOTE_PROMPT_ID);
        ids.put(InferenceTaskType.VALIDATION, InMemoryPromptRegistry.VALIDATION_PROMPT_ID);
        ids.put(InferenceTaskType.MEETING_TRIAGE, InMemoryPromptRegistry.DEFAULT_EXTRACTION_PROMPT_ID);
        ids.put(InferenceTaskType.NORMALIZE, InMemoryPromptRegistry.DEFAULT_EXTRACTION_PROMPT_ID);
        ids.put(InferenceTaskType.CHUNK_PLAN, InMemoryPromptRegistry.DEFAULT_EXTRACTION_PROMPT_ID);
        ids.put(InferenceTaskType.EMBEDDING, InMemoryPromptRegistry.DEFAULT_EXTRACTION_PROMPT_ID);
        return ids;
    }

    @Override
    public ResolvedInferenceInput resolve(AiJob job, InferenceTaskType taskType) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(taskType, "taskType");
        String promptId = promptIds.get(taskType);
        if (promptId == null) {
            throw new IllegalStateException("No prompt configured for task type " + taskType);
        }
        PublishedPrompt prompt = promptRegistry.requirePublished(promptId);
        return ResolvedInferenceInput.of(prompt.template(), userPrompt(job, taskType, prompt));
    }

    private static String userPrompt(AiJob job, InferenceTaskType taskType, PublishedPrompt prompt) {
        return """
                taskType: %s
                language: %s
                promptVersion: %s
                outputSchema: %s
                meetingOccurrenceId: %s
                transcriptId: %s
                """.formatted(
                taskType.name(),
                job.language(),
                prompt.promptVersionId(),
                prompt.outputSchemaId(),
                job.meetingOccurrenceId(),
                job.transcriptId()
        );
    }
}
