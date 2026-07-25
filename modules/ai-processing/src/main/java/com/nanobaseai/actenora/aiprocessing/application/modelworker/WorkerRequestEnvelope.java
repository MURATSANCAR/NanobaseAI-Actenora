package com.nanobaseai.actenora.aiprocessing.application.modelworker;

import com.nanobaseai.actenora.aiprocessing.domain.routing.InferenceTaskType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Worker request envelope exchanged between the AI job layer and a model worker.
 * Contains references and metadata only — never raw transcript or prompt text.
 */
public record WorkerRequestEnvelope(
        UUID jobId,
        UUID attemptId,
        InferenceTaskType taskType,
        UUID modelId,
        String servedModelId,
        String promptVersion,
        String schemaVersion,
        int timeoutSeconds,
        Map<String, Object> inputReference,
        GenerationParameters generationParameters
) {
    public WorkerRequestEnvelope {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(taskType, "taskType");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(servedModelId, "servedModelId");
        if (servedModelId.isBlank()) {
            throw LocalModelProviderException.of(
                    ProviderFailureCategory.INVALID_SERVED_MODEL,
                    "servedModelId must not be blank",
                    false
            );
        }
        Objects.requireNonNull(promptVersion, "promptVersion");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be > 0");
        }
        inputReference = Collections.unmodifiableMap(
                new LinkedHashMap<>(inputReference == null ? Map.of() : inputReference));
        generationParameters = generationParameters == null
                ? GenerationParameters.empty()
                : generationParameters;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID jobId;
        private UUID attemptId;
        private InferenceTaskType taskType;
        private UUID modelId;
        private String servedModelId;
        private String promptVersion = "unset";
        private String schemaVersion = "unset";
        private int timeoutSeconds = 600;
        private Map<String, Object> inputReference = Map.of();
        private GenerationParameters generationParameters = GenerationParameters.empty();

        public Builder jobId(UUID jobId) {
            this.jobId = jobId;
            return this;
        }

        public Builder attemptId(UUID attemptId) {
            this.attemptId = attemptId;
            return this;
        }

        public Builder taskType(InferenceTaskType taskType) {
            this.taskType = taskType;
            return this;
        }

        public Builder modelId(UUID modelId) {
            this.modelId = modelId;
            return this;
        }

        public Builder servedModelId(String servedModelId) {
            this.servedModelId = servedModelId;
            return this;
        }

        public Builder promptVersion(String promptVersion) {
            this.promptVersion = promptVersion;
            return this;
        }

        public Builder schemaVersion(String schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public Builder timeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public Builder inputReference(Map<String, Object> inputReference) {
            this.inputReference = inputReference;
            return this;
        }

        public Builder generationParameters(GenerationParameters generationParameters) {
            this.generationParameters = generationParameters;
            return this;
        }

        public WorkerRequestEnvelope build() {
            return new WorkerRequestEnvelope(
                    jobId,
                    attemptId,
                    taskType,
                    modelId,
                    servedModelId,
                    promptVersion,
                    schemaVersion,
                    timeoutSeconds,
                    inputReference,
                    generationParameters
            );
        }
    }
}
