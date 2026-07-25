package com.nanobaseai.actenora.approval.application;

import com.nanobaseai.actenora.approval.api.ApprovalDecisionType;

import java.util.UUID;

public record DecideApprovalCommand(
        UUID tenantId,
        UUID approvalRequestId,
        String actorId,
        ApprovalDecisionType decisionType,
        String comment,
        long expectedVersion
) {
}
