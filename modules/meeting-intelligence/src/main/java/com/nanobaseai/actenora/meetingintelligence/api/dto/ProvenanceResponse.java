package com.nanobaseai.actenora.meetingintelligence.api.dto;

public record ProvenanceResponse(
        String modelId,
        String promptVersionId,
        String schemaId,
        double aiConfidence
) {
}
