package com.nanobaseai.actenora.aiprocessing.domain.prompt;

/**
 * Immutable published prompt version bound to an output schema.
 */
public record PublishedPrompt(
        String promptVersionId,
        String promptId,
        int version,
        String template,
        String outputSchemaId,
        String modelCapabilityRequired
) {
    public PublishedPrompt {
        if (promptVersionId == null || promptVersionId.isBlank()) {
            throw new IllegalArgumentException("promptVersionId is required");
        }
        if (promptId == null || promptId.isBlank()) {
            throw new IllegalArgumentException("promptId is required");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("template is required");
        }
        if (outputSchemaId == null || outputSchemaId.isBlank()) {
            throw new IllegalArgumentException("outputSchemaId is required");
        }
    }
}
