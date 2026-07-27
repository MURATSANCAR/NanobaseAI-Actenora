package com.nanobaseai.actenora.aiprocessing.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.HealthStatus;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.InferenceResult;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.LocalModelProviderException;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderFailureCategory;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bridges FAZ 13 {@link LocalModelProvider} into the FAZ 14 pipeline port.
 * Prefers strict {@code response_format.json_schema}; falls back once to {@code json_object}.
 */
public final class LocalProviderModelRuntimeAdapter implements ModelRuntimePort {

    private static final Logger log = LoggerFactory.getLogger(LocalProviderModelRuntimeAdapter.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 1800;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicLong JSON_SCHEMA_FALLBACK_TOTAL = new AtomicLong();
    private static final Map<String, Object> SCHEMA_CACHE = new ConcurrentHashMap<>();

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

    /** Exposed for OTel / ops gauges. */
    public static long jsonSchemaFallbackTotal() {
        return JSON_SCHEMA_FALLBACK_TOTAL.get();
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
        Map<String, Object> schemaFormat = jsonSchemaResponseFormat(request);
        try {
            return submit(request, servedModelId, schemaFormat != null ? schemaFormat : jsonObjectFormat());
        } catch (LocalModelProviderException ex) {
            if (schemaFormat != null && isSchemaUnsupported(ex)) {
                JSON_SCHEMA_FALLBACK_TOTAL.incrementAndGet();
                log.warn("json_schema_fallback taskType={} reason={}", request.taskType(), ex.getMessage());
                try {
                    return submit(request, servedModelId, jsonObjectFormat());
                } catch (LocalModelProviderException fallbackEx) {
                    throw new ModelUnavailableException(fallbackEx.getMessage(), fallbackEx);
                }
            }
            throw new ModelUnavailableException(ex.getMessage(), ex);
        }
    }

    private InferenceResponse submit(
            InferenceRequest request,
            String servedModelId,
            Map<String, Object> responseFormat
    ) throws LocalModelProviderException {
        WorkerRequestEnvelope envelope = WorkerRequestEnvelope.builder()
                .jobId(UUID.randomUUID())
                .attemptId(UUID.randomUUID())
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
                        .maxTokens(request.maxOutputTokens() > 0
                                ? request.maxOutputTokens()
                                : MeetingLlmBudgets.DEFAULT_MAX_TOKENS)
                        .stream(true)
                        .extra("repeat_penalty", 1.05)
                        .extra("response_format", responseFormat)
                        .build())
                .build();
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
    }

    private static Map<String, Object> jsonObjectFormat() {
        return Map.of("type", "json_object");
    }

    private static Map<String, Object> jsonSchemaResponseFormat(InferenceRequest request) {
        String resource = schemaResourceFor(request);
        if (resource == null) {
            return null;
        }
        Object schema = SCHEMA_CACHE.computeIfAbsent(resource, LocalProviderModelRuntimeAdapter::loadSchema);
        if (schema == null) {
            return null;
        }
        Map<String, Object> jsonSchema = new LinkedHashMap<>();
        jsonSchema.put("name", schemaNameFor(request));
        jsonSchema.put("strict", true);
        jsonSchema.put("schema", schema);
        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("json_schema", jsonSchema);
        return format;
    }

    private static String schemaResourceFor(InferenceRequest request) {
        String task = request.taskType() == null ? "" : request.taskType();
        String schemaVersion = request.schemaVersion() == null ? "" : request.schemaVersion();
        if (task.contains("FINAL") || schemaVersion.contains("final-note") || schemaVersion.contains("final-minutes")) {
            return "/aiprocessing/schemas/final-minutes.schema.json";
        }
        if (task.contains("VALID") || task.contains("AUDIT") || schemaVersion.contains("evidence-audit")) {
            return "/aiprocessing/schemas/evidence-audit.schema.json";
        }
        if (task.contains("EXTRACT") || task.contains("MERGE") || task.contains("TRIAGE")
                || schemaVersion.contains("extraction")) {
            return "/aiprocessing/schemas/extraction-output.schema.json";
        }
        return "/aiprocessing/schemas/extraction-output.schema.json";
    }

    private static String schemaNameFor(InferenceRequest request) {
        String resource = schemaResourceFor(request);
        if (resource != null && resource.contains("final-minutes")) {
            return "final_minutes";
        }
        if (resource != null && resource.contains("evidence-audit")) {
            return "evidence_audit";
        }
        return "extraction_output";
    }

    private static Object loadSchema(String resource) {
        try (InputStream in = LocalProviderModelRuntimeAdapter.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return MAPPER.readValue(json, Object.class);
        } catch (Exception ex) {
            log.warn("Failed to load schema resource {}: {}", resource, ex.toString());
            return null;
        }
    }

    private static boolean isSchemaUnsupported(LocalModelProviderException ex) {
        if (ex.category() == ProviderFailureCategory.PROVIDER_ERROR
                || ex.category() == ProviderFailureCategory.MALFORMED_RESPONSE) {
            return true;
        }
        String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        return msg.contains("json_schema")
                || msg.contains("response_format")
                || msg.contains("400")
                || msg.contains("unsupported");
    }

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
