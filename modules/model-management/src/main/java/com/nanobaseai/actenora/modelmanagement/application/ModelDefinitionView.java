package com.nanobaseai.actenora.modelmanagement.application;

import com.nanobaseai.actenora.modelmanagement.domain.DeploymentStatus;
import com.nanobaseai.actenora.modelmanagement.domain.ModelCapabilityType;
import com.nanobaseai.actenora.modelmanagement.domain.ModelStatus;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ModelDefinitionView(
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
        Set<String> supportedLanguages,
        ModelStatus status,
        int priority,
        double qualityScore,
        double speedScore,
        Instant createdAt,
        Instant updatedAt,
        long version,
        List<CapabilityView> capabilities
) {
    public record CapabilityView(
            ModelCapabilityType capability,
            double qualityScore,
            double speedScore,
            int minContextRequired,
            boolean enabled
    ) {
    }
}
