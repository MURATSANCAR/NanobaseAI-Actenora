package com.nanobaseai.actenora.meetingintelligence.api.dto;

import com.nanobaseai.actenora.meetingintelligence.domain.model.HumanApprovalStatus;

import java.time.Instant;
import java.util.UUID;

public record IssueResponse(
        UUID id,
        UUID noteId,
        String text,
        boolean requiresManualReview,
        Double aiConfidence,
        HumanApprovalStatus humanApprovalStatus,
        long version,
        Instant updatedAt
) {
}
