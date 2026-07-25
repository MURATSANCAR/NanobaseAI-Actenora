package com.nanobaseai.actenora.modelmanagement.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Registered local LLM model catalog entry (aggregate root).
 */
public final class ModelDefinition {

    private final UUID id;
    private final String modelKey;
    private String displayName;
    private String providerType;
    private String servedModelId;
    private String modelFamily;
    private String parameterSize;
    private String quantization;
    private int contextWindow;
    private int maxOutputTokens;
    private final Set<String> supportedLanguages;
    private ModelStatus status;
    private int priority;
    private double qualityScore;
    private double speedScore;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;
    private final Map<ModelCapabilityType, ModelCapability> capabilities = new EnumMap<>(ModelCapabilityType.class);

    public ModelDefinition(
            UUID id,
            String modelKey,
            String displayName,
            String providerType,
            String servedModelId,
            String modelFamily,
            String parameterSize,
            String quantization,
            int contextWindow,
            int maxOutputTokens,
            Collection<String> supportedLanguages,
            ModelStatus status,
            int priority,
            double qualityScore,
            double speedScore,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.modelKey = requireKey(modelKey, "modelKey");
        this.displayName = requireText(displayName, "displayName");
        this.providerType = requireText(providerType, "providerType");
        this.servedModelId = requireText(servedModelId, "servedModelId");
        this.modelFamily = requireText(modelFamily, "modelFamily");
        this.parameterSize = parameterSize;
        this.quantization = quantization;
        validateContext(contextWindow, maxOutputTokens);
        this.contextWindow = contextWindow;
        this.maxOutputTokens = maxOutputTokens;
        this.supportedLanguages = normalizeLanguages(supportedLanguages);
        this.status = Objects.requireNonNull(status, "status");
        this.priority = priority;
        this.qualityScore = qualityScore;
        this.speedScore = speedScore;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    public static ModelDefinition register(
            String modelKey,
            String displayName,
            String providerType,
            String servedModelId,
            String modelFamily,
            String parameterSize,
            String quantization,
            int contextWindow,
            int maxOutputTokens,
            Collection<String> supportedLanguages,
            int priority,
            double qualityScore,
            double speedScore,
            Instant now
    ) {
        return new ModelDefinition(
                UUID.randomUUID(),
                modelKey,
                displayName,
                providerType,
                servedModelId,
                modelFamily,
                parameterSize,
                quantization,
                contextWindow,
                maxOutputTokens,
                supportedLanguages,
                ModelStatus.ENABLED,
                priority,
                qualityScore,
                speedScore,
                now,
                now,
                0L
        );
    }

    public void update(
            String displayName,
            String providerType,
            String servedModelId,
            String modelFamily,
            String parameterSize,
            String quantization,
            int contextWindow,
            int maxOutputTokens,
            Collection<String> supportedLanguages,
            Integer priority,
            Double qualityScore,
            Double speedScore,
            Instant now
    ) {
        validateContext(contextWindow, maxOutputTokens);
        for (ModelCapability capability : capabilities.values()) {
            capability.assertFitsModelContext(contextWindow);
        }
        this.displayName = requireText(displayName, "displayName");
        this.providerType = requireText(providerType, "providerType");
        this.servedModelId = requireText(servedModelId, "servedModelId");
        this.modelFamily = requireText(modelFamily, "modelFamily");
        this.parameterSize = parameterSize;
        this.quantization = quantization;
        this.contextWindow = contextWindow;
        this.maxOutputTokens = maxOutputTokens;
        this.supportedLanguages.clear();
        this.supportedLanguages.addAll(normalizeLanguages(supportedLanguages));
        if (priority != null) {
            this.priority = priority;
        }
        if (qualityScore != null) {
            this.qualityScore = qualityScore;
        }
        if (speedScore != null) {
            this.speedScore = speedScore;
        }
        touch(now);
    }

    public void configureCapability(ModelCapability capability, Instant now) {
        Objects.requireNonNull(capability, "capability");
        capability.assertFitsModelContext(contextWindow);
        capabilities.put(capability.capability(), capability);
        touch(now);
    }

    public void enable(Instant now) {
        if (status == ModelStatus.RETIRED) {
            throw ModelRegistryException.invalidState("Cannot enable a retired model");
        }
        status = ModelStatus.ENABLED;
        touch(now);
    }

    public void disable(Instant now) {
        if (status == ModelStatus.RETIRED) {
            throw ModelRegistryException.invalidState("Cannot disable a retired model");
        }
        status = ModelStatus.DISABLED;
        touch(now);
    }

    /**
     * Drain stops admitting new work while allowing in-flight jobs to finish.
     */
    public void drain(Instant now) {
        if (status == ModelStatus.RETIRED || status == ModelStatus.DISABLED) {
            throw ModelRegistryException.invalidState(
                    "Cannot drain model in status " + status
            );
        }
        status = ModelStatus.DRAINING;
        touch(now);
    }

    public boolean acceptsNewWork() {
        return status.acceptsNewWork();
    }

    public boolean supportsCapability(ModelCapabilityType type) {
        ModelCapability capability = capabilities.get(type);
        return capability != null && capability.enabled();
    }

    public Optional<ModelCapability> capability(ModelCapabilityType type) {
        return Optional.ofNullable(capabilities.get(type));
    }

    public Map<ModelCapabilityType, ModelCapability> capabilities() {
        return Collections.unmodifiableMap(capabilities);
    }

    public UUID id() {
        return id;
    }

    public String modelKey() {
        return modelKey;
    }

    public String displayName() {
        return displayName;
    }

    public String providerType() {
        return providerType;
    }

    public String servedModelId() {
        return servedModelId;
    }

    public String modelFamily() {
        return modelFamily;
    }

    public String parameterSize() {
        return parameterSize;
    }

    public String quantization() {
        return quantization;
    }

    public int contextWindow() {
        return contextWindow;
    }

    public int maxOutputTokens() {
        return maxOutputTokens;
    }

    public Set<String> supportedLanguages() {
        return Collections.unmodifiableSet(supportedLanguages);
    }

    public ModelStatus status() {
        return status;
    }

    public int priority() {
        return priority;
    }

    public double qualityScore() {
        return qualityScore;
    }

    public double speedScore() {
        return speedScore;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }

    private void touch(Instant now) {
        this.updatedAt = Objects.requireNonNull(now, "now");
        this.version++;
    }

    private static void validateContext(int contextWindow, int maxOutputTokens) {
        if (contextWindow <= 0) {
            throw ModelRegistryException.invalidContextSize("context_window must be > 0");
        }
        if (maxOutputTokens <= 0) {
            throw ModelRegistryException.invalidContextSize("max_output_tokens must be > 0");
        }
        if (maxOutputTokens > contextWindow) {
            throw ModelRegistryException.invalidContextSize(
                    "max_output_tokens (" + maxOutputTokens
                            + ") cannot exceed context_window (" + contextWindow + ")"
            );
        }
    }

    private static String requireKey(String value, String name) {
        String text = requireText(value, name);
        if (!text.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}")) {
            throw new IllegalArgumentException(name + " has invalid format");
        }
        return text;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }

    private static Set<String> normalizeLanguages(Collection<String> languages) {
        Set<String> normalized = new LinkedHashSet<>();
        if (languages != null) {
            for (String language : languages) {
                if (language != null && !language.isBlank()) {
                    normalized.add(language.trim().toLowerCase());
                }
            }
        }
        return normalized;
    }
}
