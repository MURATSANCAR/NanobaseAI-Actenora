package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Catalog projection used by FAZ 12 ModelRouter (sourced from model-management).
 */
public record RoutableCandidate(
        UUID modelDefinitionId,
        String modelKey,
        UUID deploymentId,
        String deploymentKey,
        Set<AiCapability> enabledCapabilities,
        int contextWindow,
        int minContextRequired,
        Set<String> supportedLanguages,
        boolean healthy,
        boolean modelAcceptsWork,
        boolean deploymentAcceptsWork,
        int maxConcurrency,
        int currentConcurrency,
        int queueDepth,
        double qualityScore,
        double speedScore,
        int modelPriority
) {
    public RoutableCandidate {
        Objects.requireNonNull(modelDefinitionId, "modelDefinitionId");
        Objects.requireNonNull(modelKey, "modelKey");
        Objects.requireNonNull(deploymentId, "deploymentId");
        Objects.requireNonNull(deploymentKey, "deploymentKey");
        Objects.requireNonNull(enabledCapabilities, "enabledCapabilities");
        enabledCapabilities = Set.copyOf(enabledCapabilities);
        Objects.requireNonNull(supportedLanguages, "supportedLanguages");
        supportedLanguages = Set.copyOf(supportedLanguages);
        if (contextWindow <= 0) {
            throw new IllegalArgumentException("contextWindow must be > 0");
        }
        if (minContextRequired < 0) {
            throw new IllegalArgumentException("minContextRequired must be >= 0");
        }
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException("maxConcurrency must be > 0");
        }
        if (currentConcurrency < 0 || queueDepth < 0) {
            throw new IllegalArgumentException("concurrency/queueDepth must be >= 0");
        }
    }

    public boolean hasCapacity() {
        return currentConcurrency < maxConcurrency;
    }

    public boolean supportsLanguage(String language) {
        if (language == null || language.isBlank()) {
            return true;
        }
        return supportedLanguages.contains(language.trim().toLowerCase());
    }

    public boolean fitsContext(int contextSize) {
        return contextWindow >= contextSize && contextWindow >= minContextRequired;
    }
}
