package com.nanobaseai.actenora.aiprocessing.application.pipeline;

/**
 * Catalog / served model identity returned by the runtime adapter (not domain-hardcoded).
 */
public record ModelDescriptor(
        String modelCatalogId,
        String servedModelId,
        String modelVersion,
        int contextWindowTokens,
        int maxOutputTokens
) {
    public ModelDescriptor {
        if (modelCatalogId == null || modelCatalogId.isBlank()) {
            throw new IllegalArgumentException("modelCatalogId is required");
        }
        if (servedModelId == null || servedModelId.isBlank()) {
            throw new IllegalArgumentException("servedModelId is required");
        }
        if (modelVersion == null || modelVersion.isBlank()) {
            throw new IllegalArgumentException("modelVersion is required");
        }
        if (contextWindowTokens <= 0) {
            throw new IllegalArgumentException("contextWindowTokens must be > 0");
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be > 0");
        }
    }
}
