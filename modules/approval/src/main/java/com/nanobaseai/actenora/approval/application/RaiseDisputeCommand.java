package com.nanobaseai.actenora.approval.application;

import com.nanobaseai.actenora.approval.api.ApprovalSubjectType;

import java.util.UUID;

public record RaiseDisputeCommand(
        UUID tenantId,
        UUID subjectId,
        ApprovalSubjectType subjectType,
        String participantId,
        String proposedContent,
        String reason
) {
}
