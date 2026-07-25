package com.nanobaseai.actenora.modelmanagement.application;

import java.util.List;

public record UpdateModelCommand(
        String displayName,
        String providerType,
        String servedModelId,
        String modelFamily,
        String parameterSize,
        String quantization,
        int contextWindow,
        int maxOutputTokens,
        List<String> supportedLanguages,
        Integer priority,
        Double qualityScore,
        Double speedScore
) {
}
