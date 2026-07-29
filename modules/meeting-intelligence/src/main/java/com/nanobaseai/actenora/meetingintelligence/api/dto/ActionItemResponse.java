package com.nanobaseai.actenora.meetingintelligence.api.dto;

import com.nanobaseai.actenora.meetingintelligence.domain.model.ActionItemStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.HumanApprovalStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ActionItemResponse(
        UUID id,
        UUID noteId,
        String text,
        String owner,
        LocalDate dueDate,
        ActionItemStatus status,
        boolean requiresManualReview,
        Double aiConfidence,
        HumanApprovalStatus humanApprovalStatus,
        String ownerType,
        String priority,
        String relativeDate,
        Instant dueAt,
        long version,
        Instant updatedAt
) {
}
