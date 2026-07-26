package com.nanobaseai.actenora.approval.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public façade for the Approval bounded context.
 * Cross-module callers use types in this package only.
 */
public interface ApprovalApi {

    ApprovalId openSingleStage(
            UUID tenantId,
            ApprovalSubjectType subjectType,
            UUID subjectId,
            String approverId,
            Instant expiresAt
    );

    ApprovalId open(
            UUID tenantId,
            ApprovalSubjectType subjectType,
            UUID subjectId,
            List<String> orderedApproverIds,
            Instant expiresAt
    );

    ApprovalRequestStatus decide(
            UUID tenantId,
            ApprovalId approvalId,
            String actorId,
            ApprovalDecisionType decisionType,
            String comment,
            long expectedVersion
    );

    boolean isGranted(UUID tenantId, ApprovalId approvalId);

    boolean isGrantedForSubject(UUID tenantId, ApprovalId approvalId, UUID subjectId);

    Optional<ApprovalRequestStatus> status(UUID tenantId, ApprovalId approvalId);

    Optional<Long> version(UUID tenantId, ApprovalId approvalId);

    Optional<ApprovalRequestView> get(UUID tenantId, ApprovalId approvalId);

    Optional<ApprovalId> findBySubject(UUID tenantId, UUID subjectId);

    UUID raiseDispute(
            UUID tenantId,
            UUID subjectId,
            ApprovalSubjectType subjectType,
            String participantId,
            String proposedContent,
            String reason
    );

    /**
     * Accepts a dispute and returns proposed content for a new draft. Never overwrites in place.
     */
    String acceptDispute(UUID tenantId, UUID disputeId, String resolverId);

    void rejectDispute(UUID tenantId, UUID disputeId, String resolverId);
}
