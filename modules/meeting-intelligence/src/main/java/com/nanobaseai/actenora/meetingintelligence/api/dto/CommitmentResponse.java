package com.nanobaseai.actenora.meetingintelligence.api.dto;

import com.nanobaseai.actenora.meetingintelligence.domain.model.CommitmentConfirmationStatus;

import java.time.Instant;
import java.util.UUID;

public record CommitmentResponse(
        UUID id,
        UUID noteId,
        String text,
        String owner,
        CommitmentConfirmationStatus confirmationStatus,
        boolean requiresManualReview,
        Double aiConfidence,
        UUID decidedByUserId,
        Instant decidedAt,
        long version,
        Instant updatedAt
) {
}
