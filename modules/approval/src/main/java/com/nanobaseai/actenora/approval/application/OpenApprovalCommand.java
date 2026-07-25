package com.nanobaseai.actenora.approval.application;

import com.nanobaseai.actenora.approval.api.ApprovalSubjectType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OpenApprovalCommand(
        UUID tenantId,
        ApprovalSubjectType subjectType,
        UUID subjectId,
        List<String> orderedApproverIds,
        Instant expiresAt
) {
    public OpenApprovalCommand {
        if (orderedApproverIds == null || orderedApproverIds.isEmpty()) {
            throw new IllegalArgumentException("orderedApproverIds required");
        }
    }

    public static OpenApprovalCommand singleStage(
            UUID tenantId,
            ApprovalSubjectType subjectType,
            UUID subjectId,
            String approverId,
            Instant expiresAt
    ) {
        return new OpenApprovalCommand(tenantId, subjectType, subjectId, List.of(approverId), expiresAt);
    }
}
