package com.nanobaseai.actenora.modelmanagement.application;

import java.util.List;

public record RegisterModelCommand(
        String modelKey,
        String displayName,
        String providerType,
        String servedModelId,
        String modelFamily,
        String parameterSize,
        String quantization,
        int contextWindow,
        int maxOutputTokens,
        List<String> supportedLanguages,
        int priority,
        double qualityScore,
        double speedScore
) {
}
