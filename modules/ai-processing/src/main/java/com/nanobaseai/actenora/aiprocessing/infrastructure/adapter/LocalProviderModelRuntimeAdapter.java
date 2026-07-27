package com.nanobaseai.actenora.aiprocessing.infrastructure.adapter;

import com.nanobaseai.actenora.aiprocessing.application.modelworker.HealthStatus;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.InferenceResult;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.LocalModelProviderException;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderHealth;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ResolvedInferenceInput;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.WorkerRequestEnvelope;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.InferenceRequest;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.InferenceResponse;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelDescriptor;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelRuntimePort;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.ModelUnavailableException;
import com.nanobaseai.actenora.aiprocessing.application.port.LocalModelProvider;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.MeetingLlmBudgets;
import com.nanobaseai.actenora.aiprocessing.domain.routing.InferenceTaskType;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Bridges FAZ 13 {@link LocalModelProvider} into the FAZ 14 pipeline port.
 * Qwen served-model identity lives only in the descriptor supplied at construction.
 */
public final class LocalProviderModelRuntimeAdapter implements ModelRuntimePort {

    private static final int DEFAULT_TIMEOUT_SECONDS = 1800;

    private final LocalModelProvider provider;
    private final ModelDescriptor descriptor;
    private final UUID modelDefinitionId;
    private final int defaultTimeoutSeconds;

    public LocalProviderModelRuntimeAdapter(
            LocalModelProvider provider,
            ModelDescriptor descriptor,
            UUID modelDefinitionId
    ) {
        this(provider, descriptor, modelDefinitionId, DEFAULT_TIMEOUT_SECONDS);
    }

    public LocalProviderModelRuntimeAdapter(
            LocalModelProvider provider,
            ModelDescriptor descriptor,
            UUID modelDefinitionId,
            int defaultTimeoutSeconds
    ) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.modelDefinitionId = Objects.requireNonNull(modelDefinitionId, "modelDefinitionId");
        if (defaultTimeoutSeconds < 1) {
            throw new IllegalArgumentException("defaultTimeoutSeconds must be >= 1");
        }
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
    }

    /**
     * Production wiring for the first local reasoner (Qwen 27B-class) behind catalog ids.
     */
    public static LocalProviderModelRuntimeAdapter qwen27B(LocalModelProvider provider, UUID modelDefinitionId) {
        return qwen27B(provider, modelDefinitionId, DEFAULT_TIMEOUT_SECONDS);
    }

    public static LocalProviderModelRuntimeAdapter qwen27B(
            LocalModelProvider provider,
            UUID modelDefinitionId,
            int defaultTimeoutSeconds
    ) {
        return new LocalProviderModelRuntimeAdapter(
                provider,
                new ModelDescriptor(
                        Qwen27BModelAdapter.CATALOG_ID,
                        Qwen27BModelAdapter.SERVED_MODEL_ID,
                        Qwen27BModelAdapter.MODEL_VERSION,
                        Qwen27BModelAdapter.CONTEXT_WINDOW,
                        Qwen27BModelAdapter.MAX_OUTPUT
                ),
                modelDefinitionId,
                defaultTimeoutSeconds
        );
    }

    @Override
    public ModelDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public boolean healthy() {
        ProviderHealth health = provider.health();
        return health != null && health.status() != HealthStatus.DOWN;
    }

    @Override
    public InferenceResponse infer(InferenceRequest request) {
        if (!healthy()) {
            throw new ModelUnavailableException("Local model provider unhealthy");
        }
        String servedModelId = resolveServedModelId();
        UUID jobId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        WorkerRequestEnvelope envelope = WorkerRequestEnvelope.builder()
                .jobId(jobId)
                .attemptId(attemptId)
                .taskType(InferenceTaskType.valueOf(request.taskType()))
                .modelId(modelDefinitionId)
                .servedModelId(servedModelId)
                .promptVersion(request.promptVersionId())
                .schemaVersion(request.schemaVersion())
                .timeoutSeconds(request.timeoutSeconds() > 0 ? request.timeoutSeconds() : defaultTimeoutSeconds)
                .inputReference(Map.of("evidenceCount", request.allowedEvidenceSegmentIds().size()))
                .generationParameters(com.nanobaseai.actenora.aiprocessing.application.modelworker.GenerationParameters.builder()
                        .temperature(0.1)
                        .topP(0.85)
                        .topK(20)
                        // Stage callers set maxOutputTokens; fallback stays at meeting-pipeline ceiling.
                        .maxTokens(request.maxOutputTokens() > 0
                                ? request.maxOutputTokens()
                                : MeetingLlmBudgets.DEFAULT_MAX_TOKENS)
                        .stream(true)
                        .extra("repeat_penalty", 1.05)
                        .extra("response_format", java.util.Map.of("type", "json_object"))
                        .build())
                .build();
        try {
            InferenceResult result = provider.submitInference(
                    envelope,
                    ResolvedInferenceInput.of(request.systemPrompt(), request.userPrompt())
            );
            return new InferenceResponse(
                    result.content(),
                    result.tokenUsage().inputTokens(),
                    result.tokenUsage().outputTokens(),
                    result.latencyMs(),
                    servedModelId + "@local-v1"
            );
        } catch (LocalModelProviderException ex) {
            throw new ModelUnavailableException(ex.getMessage(), ex);
        }
    }

    /**
     * Prefer a concrete served model id. Placeholder aliases ({@code nanobaseai-local}) must never
     * win over a real registry/runtime id — llama.cpp echoes the concrete model name and FAZ 13
     * fails closed on mismatch.
     */
    private String resolveServedModelId() {
        String configured = descriptor.servedModelId();
        java.util.function.Predicate<String> concrete = id -> id != null
                && !id.isBlank()
                && !id.equals("nanobaseai-primary")
                && !id.equals("nanobaseai-local");
        if (concrete.test(configured)) {
            return configured;
        }
        var known = provider.capabilities() == null
                ? java.util.Set.<String>of()
                : provider.capabilities().servedModelIds();
        if (known == null || known.isEmpty()) {
            return configured;
        }
        return known.stream()
                .filter(concrete)
                .findFirst()
                .or(() -> known.stream().filter(id -> id != null && !id.isBlank()).findFirst())
                .orElse(configured);
    }
}
