package com.nanobaseai.actenora.approval.infrastructure;

import com.nanobaseai.actenora.approval.api.ApprovalApi;
import com.nanobaseai.actenora.approval.api.ApprovalDecisionType;
import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.approval.api.ApprovalRequestStatus;
import com.nanobaseai.actenora.approval.api.ApprovalSubjectType;
import com.nanobaseai.actenora.approval.application.ApprovalWorkflowService;
import com.nanobaseai.actenora.approval.application.DecideApprovalCommand;
import com.nanobaseai.actenora.approval.application.OpenApprovalCommand;
import com.nanobaseai.actenora.approval.application.RaiseDisputeCommand;
import com.nanobaseai.actenora.approval.application.ResolveDisputeCommand;
import com.nanobaseai.actenora.approval.domain.ApprovalRequest;
import com.nanobaseai.actenora.approval.domain.ParticipantDispute;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter exposing {@link ApprovalWorkflowService} through the public {@link ApprovalApi}.
 */
public final class ApprovalApiAdapter implements ApprovalApi {

    private final ApprovalWorkflowService workflow;

    public ApprovalApiAdapter(ApprovalWorkflowService workflow) {
        this.workflow = Objects.requireNonNull(workflow, "workflow");
    }

    @Override
    public ApprovalId openSingleStage(
            UUID tenantId,
            ApprovalSubjectType subjectType,
            UUID subjectId,
            String approverId,
            Instant expiresAt
    ) {
        ApprovalRequest saved = workflow.open(
                OpenApprovalCommand.singleStage(tenantId, subjectType, subjectId, approverId, expiresAt)
        );
        return ApprovalId.of(saved.id());
    }

    @Override
    public ApprovalId open(
            UUID tenantId,
            ApprovalSubjectType subjectType,
            UUID subjectId,
            List<String> orderedApproverIds,
            Instant expiresAt
    ) {
        ApprovalRequest saved = workflow.open(
                new OpenApprovalCommand(tenantId, subjectType, subjectId, orderedApproverIds, expiresAt)
        );
        return ApprovalId.of(saved.id());
    }

    @Override
    public ApprovalRequestStatus decide(
            UUID tenantId,
            ApprovalId approvalId,
            String actorId,
            ApprovalDecisionType decisionType,
            String comment,
            long expectedVersion
    ) {
        ApprovalRequest saved = workflow.decide(new DecideApprovalCommand(
                tenantId, approvalId.value(), actorId, decisionType, comment, expectedVersion
        ));
        return saved.status();
    }

    @Override
    public boolean isGranted(UUID tenantId, ApprovalId approvalId) {
        return workflow.isGranted(tenantId, approvalId.value());
    }

    @Override
    public boolean isGrantedForSubject(UUID tenantId, ApprovalId approvalId, UUID subjectId) {
        return workflow.isGrantedForSubject(tenantId, approvalId.value(), subjectId);
    }

    @Override
    public Optional<ApprovalRequestStatus> status(UUID tenantId, ApprovalId approvalId) {
        return workflow.findById(tenantId, approvalId.value()).map(ApprovalRequest::status);
    }

    @Override
    public Optional<Long> version(UUID tenantId, ApprovalId approvalId) {
        return workflow.findById(tenantId, approvalId.value()).map(ApprovalRequest::version);
    }

    @Override
    public Optional<ApprovalId> findBySubject(UUID tenantId, UUID subjectId) {
        return workflow.findBySubject(tenantId, subjectId).map(r -> ApprovalId.of(r.id()));
    }

    @Override
    public UUID raiseDispute(
            UUID tenantId,
            UUID subjectId,
            ApprovalSubjectType subjectType,
            String participantId,
            String proposedContent,
            String reason
    ) {
        return workflow.raiseDispute(new RaiseDisputeCommand(
                tenantId, subjectId, subjectType, participantId, proposedContent, reason
        )).id();
    }

    @Override
    public String acceptDispute(UUID tenantId, UUID disputeId, String resolverId) {
        ParticipantDispute.AcceptedCorrection accepted = workflow.acceptDispute(
                new ResolveDisputeCommand(tenantId, disputeId, resolverId, true)
        );
        return accepted.proposedContentForNewDraft();
    }

    @Override
    public void rejectDispute(UUID tenantId, UUID disputeId, String resolverId) {
        workflow.rejectDispute(new ResolveDisputeCommand(tenantId, disputeId, resolverId, false));
    }
}
