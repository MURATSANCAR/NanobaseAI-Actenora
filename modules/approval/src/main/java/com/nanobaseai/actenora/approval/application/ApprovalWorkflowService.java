package com.nanobaseai.actenora.approval.application;

import com.nanobaseai.actenora.approval.application.port.ApprovalAuditPort;
import com.nanobaseai.actenora.approval.application.port.ApprovalRequestRepository;
import com.nanobaseai.actenora.approval.application.port.ParticipantDisputeRepository;
import com.nanobaseai.actenora.approval.domain.ApprovalDecision;
import com.nanobaseai.actenora.approval.domain.ApprovalRequest;
import com.nanobaseai.actenora.approval.domain.ParticipantDispute;
import com.nanobaseai.actenora.approval.domain.exception.ApprovalNotFoundException;
import com.nanobaseai.actenora.approval.domain.exception.DisputeNotFoundException;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for approval requests, decisions, and participant disputes.
 */
public final class ApprovalWorkflowService {

    private final ApprovalRequestRepository approvalRepository;
    private final ParticipantDisputeRepository disputeRepository;
    private final ApprovalAuditPort auditPort;
    private final Clock clock;

    public ApprovalWorkflowService(
            ApprovalRequestRepository approvalRepository,
            ParticipantDisputeRepository disputeRepository,
            ApprovalAuditPort auditPort,
            Clock clock
    ) {
        this.approvalRepository = Objects.requireNonNull(approvalRepository, "approvalRepository");
        this.disputeRepository = Objects.requireNonNull(disputeRepository, "disputeRepository");
        this.auditPort = Objects.requireNonNull(auditPort, "auditPort");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ApprovalRequest open(OpenApprovalCommand command) {
        Instant now = clock.instant();
        ApprovalRequest request = ApprovalRequest.open(
                TenantId.of(command.tenantId()),
                command.subjectType(),
                command.subjectId(),
                command.orderedApproverIds(),
                command.expiresAt(),
                now
        );
        ApprovalRequest saved = approvalRepository.save(request);
        auditPort.record(
                command.tenantId(),
                command.orderedApproverIds().getFirst(),
                "APPROVAL_REQUESTED",
                "ApprovalRequest",
                saved.id(),
                Map.of(
                        "subjectType", saved.subjectType().name(),
                        "subjectId", saved.subjectId().toString(),
                        "stepCount", saved.steps().size(),
                        "expiresAt", saved.expiresAt() == null ? "" : saved.expiresAt().toString()
                ),
                now
        );
        return saved;
    }

    public ApprovalRequest decide(DecideApprovalCommand command) {
        TenantId tenantId = TenantId.of(command.tenantId());
        ApprovalRequest request = approvalRepository
                .findById(tenantId, command.approvalRequestId())
                .orElseThrow(() -> new ApprovalNotFoundException(command.approvalRequestId()));
        request.assertVersion(command.expectedVersion());

        Instant now = clock.instant();
        request.expireIfDue(now);
        ApprovalDecision decision = request.decide(
                command.actorId(),
                command.decisionType(),
                command.comment(),
                now
        );
        ApprovalRequest saved = approvalRepository.save(request);
        auditPort.record(
                command.tenantId(),
                command.actorId(),
                "APPROVAL_DECISION_" + decision.decisionType().name(),
                "ApprovalRequest",
                saved.id(),
                Map.of(
                        "decisionId", decision.id().toString(),
                        "comment", decision.comment(),
                        "status", saved.status().name(),
                        "subjectId", saved.subjectId().toString()
                ),
                now
        );
        return saved;
    }

    public boolean isGranted(UUID tenantId, UUID approvalRequestId) {
        return approvalRepository
                .findById(TenantId.of(tenantId), approvalRequestId)
                .map(ApprovalRequest::isGranted)
                .orElse(false);
    }

    public boolean isGrantedForSubject(UUID tenantId, UUID approvalRequestId, UUID subjectId) {
        return approvalRepository
                .findById(TenantId.of(tenantId), approvalRequestId)
                .filter(ApprovalRequest::isGranted)
                .filter(r -> r.subjectId().equals(subjectId))
                .isPresent();
    }

    public Optional<ApprovalRequest> findById(UUID tenantId, UUID approvalRequestId) {
        return approvalRepository.findById(TenantId.of(tenantId), approvalRequestId);
    }

    public Optional<ApprovalRequest> findBySubject(UUID tenantId, UUID subjectId) {
        return approvalRepository.findBySubject(TenantId.of(tenantId), subjectId);
    }

    public ParticipantDispute raiseDispute(RaiseDisputeCommand command) {
        Instant now = clock.instant();
        ParticipantDispute dispute = ParticipantDispute.raise(
                command.tenantId(),
                command.subjectId(),
                command.subjectType(),
                command.participantId(),
                command.proposedContent(),
                command.reason(),
                now
        );
        ParticipantDispute saved = disputeRepository.save(dispute);
        auditPort.record(
                command.tenantId(),
                command.participantId(),
                "PARTICIPANT_DISPUTE_RAISED",
                "ParticipantDispute",
                saved.id(),
                Map.of(
                        "subjectId", saved.subjectId().toString(),
                        "reason", saved.reason()
                ),
                now
        );
        return saved;
    }

    public ParticipantDispute.AcceptedCorrection acceptDispute(ResolveDisputeCommand command) {
        ParticipantDispute dispute = requireDispute(command.tenantId(), command.disputeId());
        Instant now = clock.instant();
        ParticipantDispute.AcceptedCorrection accepted = dispute.accept(command.resolverId(), now);
        disputeRepository.save(accepted.dispute());
        auditPort.record(
                command.tenantId(),
                command.resolverId(),
                "PARTICIPANT_DISPUTE_ACCEPTED",
                "ParticipantDispute",
                accepted.dispute().id(),
                Map.of(
                        "subjectId", accepted.dispute().subjectId().toString(),
                        "requiresNewDraft", true
                ),
                now
        );
        return accepted;
    }

    public ParticipantDispute rejectDispute(ResolveDisputeCommand command) {
        ParticipantDispute dispute = requireDispute(command.tenantId(), command.disputeId());
        Instant now = clock.instant();
        ParticipantDispute rejected = dispute.reject(command.resolverId(), now);
        ParticipantDispute saved = disputeRepository.save(rejected);
        auditPort.record(
                command.tenantId(),
                command.resolverId(),
                "PARTICIPANT_DISPUTE_REJECTED",
                "ParticipantDispute",
                saved.id(),
                Map.of("subjectId", saved.subjectId().toString()),
                now
        );
        return saved;
    }

    private ParticipantDispute requireDispute(UUID tenantId, UUID disputeId) {
        return disputeRepository
                .findById(tenantId, disputeId)
                .orElseThrow(() -> new DisputeNotFoundException(disputeId));
    }
}
