package com.nanobaseai.actenora.meetingintelligence.api.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Command to map AI candidates into corporate note objects.
 *
 * <p>Called from the platform composition root (AI extraction handoff adapter), not directly
 * from the AI Processing module — Modulith forbids {@code aiprocessing → meetingintelligence}.
 */
public record MapAiCandidatesCommand(
        UUID tenantId,
        UUID meetingOccurrenceId,
        AiCandidateBundle candidates,
        String modelId,
        String promptVersionId,
        String schemaId,
        double aiConfidence
) {
    public MapAiCandidatesCommand {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(promptVersionId, "promptVersionId");
        Objects.requireNonNull(schemaId, "schemaId");
        if (aiConfidence < 0.0d || aiConfidence > 1.0d) {
            throw new IllegalArgumentException("aiConfidence must be in [0,1]");
        }
    }
}
