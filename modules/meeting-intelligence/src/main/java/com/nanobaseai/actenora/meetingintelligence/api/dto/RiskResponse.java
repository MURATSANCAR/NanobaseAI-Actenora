package com.nanobaseai.actenora.meetingintelligence.api.dto;

import com.nanobaseai.actenora.meetingintelligence.domain.model.HumanApprovalStatus;

import java.time.Instant;
import java.util.UUID;

public record RiskResponse(
        UUID id,
        UUID noteId,
        String text,
        boolean requiresManualReview,
        Double aiConfidence,
        HumanApprovalStatus humanApprovalStatus,
        String likelihood,
        String mitigation,
        long version,
        Instant updatedAt
) {
}
