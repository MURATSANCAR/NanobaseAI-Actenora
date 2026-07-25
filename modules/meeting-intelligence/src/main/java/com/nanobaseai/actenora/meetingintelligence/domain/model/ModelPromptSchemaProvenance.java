package com.nanobaseai.actenora.meetingintelligence.domain.model;

import java.util.Objects;

/**
 * Model / prompt / schema provenance for AI-originated content.
 * {@code aiConfidence} is never treated as human approval.
 */
public record ModelPromptSchemaProvenance(
        String modelId,
        String promptVersionId,
        String schemaId,
        double aiConfidence
) {
    public ModelPromptSchemaProvenance {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(promptVersionId, "promptVersionId");
        Objects.requireNonNull(schemaId, "schemaId");
        if (aiConfidence < 0.0d || aiConfidence > 1.0d) {
            throw new IllegalArgumentException("aiConfidence must be in [0,1]");
        }
    }

    public static ModelPromptSchemaProvenance of(
            String modelId,
            String promptVersionId,
            String schemaId,
            double aiConfidence
    ) {
        return new ModelPromptSchemaProvenance(modelId, promptVersionId, schemaId, aiConfidence);
    }
}
