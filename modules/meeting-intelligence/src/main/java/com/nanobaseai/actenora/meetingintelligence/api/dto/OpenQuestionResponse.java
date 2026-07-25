package com.nanobaseai.actenora.meetingintelligence.api.dto;

import java.time.Instant;
import java.util.UUID;

public record OpenQuestionResponse(
        UUID id,
        UUID noteId,
        String text,
        boolean requiresManualReview,
        Double aiConfidence,
        long version,
        Instant updatedAt
) {
}
