package com.nanobaseai.actenora.approval;

import com.nanobaseai.actenora.approval.api.ApprovalDecisionType;
import com.nanobaseai.actenora.approval.api.ApprovalRequestStatus;
import com.nanobaseai.actenora.approval.api.ApprovalSubjectType;
import com.nanobaseai.actenora.approval.application.ApprovalWorkflowService;
import com.nanobaseai.actenora.approval.application.DecideApprovalCommand;
import com.nanobaseai.actenora.approval.application.OpenApprovalCommand;
import com.nanobaseai.actenora.approval.application.RaiseDisputeCommand;
import com.nanobaseai.actenora.approval.application.ResolveDisputeCommand;
import com.nanobaseai.actenora.approval.domain.ApprovalRequest;
import com.nanobaseai.actenora.approval.domain.DisputeStatus;
import com.nanobaseai.actenora.approval.domain.ParticipantDispute;
import com.nanobaseai.actenora.approval.domain.exception.OptimisticLockConflictException;
import com.nanobaseai.actenora.approval.domain.exception.SilentOverwriteForbiddenException;
import com.nanobaseai.actenora.approval.domain.exception.UnauthorizedApprovalException;
import com.nanobaseai.actenora.approval.infrastructure.InMemoryApprovalRequestRepository;
import com.nanobaseai.actenora.approval.infrastructure.InMemoryParticipantDisputeRepository;
import com.nanobaseai.actenora.approval.infrastructure.RecordingApprovalAuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalWorkflowServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private UUID tenantId;
    private ApprovalWorkflowService service;
    private InMemoryApprovalRequestRepository approvalRepo;
    private InMemoryParticipantDisputeRepository disputeRepo;
    private RecordingApprovalAuditPort audit;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        approvalRepo = new InMemoryApprovalRequestRepository();
        disputeRepo = new InMemoryParticipantDisputeRepository();
        audit = new RecordingApprovalAuditPort();
        service = new ApprovalWorkflowService(approvalRepo, disputeRepo, audit, CLOCK);
    }

    @Test
    void unauthorizedApprovalIsRejected() {
        ApprovalRequest request = openSingle("approver-1");
        assertThrows(UnauthorizedApprovalException.class, () ->
                service.decide(new DecideApprovalCommand(
                        tenantId, request.id(), "intruder",
                        ApprovalDecisionType.APPROVE, "nope", 0L
                ))
        );
    }

    @Test
    void changesRequestedCreatesChangeRequestWithComment() {
        ApprovalRequest request = openSingle("approver-1");
        ApprovalRequest updated = service.decide(new DecideApprovalCommand(
                tenantId, request.id(), "approver-1",
                ApprovalDecisionType.REQUEST_CHANGES, "Please clarify", 0L
        ));
        assertEquals(ApprovalRequestStatus.CHANGES_REQUESTED, updated.status());
        assertEquals(1, updated.changeRequests().size());
        assertEquals("Please clarify", updated.changeRequests().getFirst().reason());
        assertEquals("Please clarify", updated.decisions().getFirst().comment());
    }

    @Test
    void concurrentUpdateUsesOptimisticLock() {
        ApprovalRequest request = openSingle("approver-1");
        service.decide(new DecideApprovalCommand(
                tenantId, request.id(), "approver-1", ApprovalDecisionType.APPROVE, "first", 0L
        ));
        assertThrows(OptimisticLockConflictException.class, () ->
                service.decide(new DecideApprovalCommand(
                        tenantId, request.id(), "approver-1", ApprovalDecisionType.APPROVE, "stale", 0L
                ))
        );
    }

    @Test
    void disputeWorkflowDoesNotSilentlyOverwrite() {
        UUID subjectId = UUID.randomUUID();
        ParticipantDispute dispute = service.raiseDispute(new RaiseDisputeCommand(
                tenantId, subjectId, ApprovalSubjectType.MEETING_NOTE_VERSION,
                "participant-1", "Corrected text", "wrong attribution"
        ));
        assertThrows(SilentOverwriteForbiddenException.class, dispute::applySilently);

        ParticipantDispute.AcceptedCorrection accepted = service.acceptDispute(
                new ResolveDisputeCommand(tenantId, dispute.id(), "resolver-1", true)
        );
        assertEquals("Corrected text", accepted.proposedContentForNewDraft());
        assertEquals(DisputeStatus.ACCEPTED, accepted.dispute().status());
    }

    @Test
    void approvalExpiryPreparation() {
        ApprovalRequest request = service.open(OpenApprovalCommand.singleStage(
                tenantId, ApprovalSubjectType.MEETING_NOTE_VERSION, UUID.randomUUID(),
                "approver-1", NOW.minusSeconds(5)
        ));
        assertTrue(request.isExpired(NOW));
        request.expireIfDue(NOW);
        assertEquals(ApprovalRequestStatus.EXPIRED, request.status());
    }

    @Test
    void multiStageModelReady_singleStageWorks() {
        ApprovalRequest multi = service.open(new OpenApprovalCommand(
                tenantId, ApprovalSubjectType.MEETING_NOTE_VERSION, UUID.randomUUID(),
                List.of("a", "b"), null
        ));
        assertEquals(2, multi.steps().size());

        ApprovalRequest single = openSingle("approver-1");
        ApprovalRequest granted = service.decide(new DecideApprovalCommand(
                tenantId, single.id(), "approver-1", ApprovalDecisionType.APPROVE, "LGTM", 0L
        ));
        assertEquals(ApprovalRequestStatus.GRANTED, granted.status());
        assertTrue(audit.timelineFor(single.id()).stream()
                .anyMatch(e -> e.action().equals("APPROVAL_DECISION_APPROVE")));
        assertEquals("LGTM", granted.decisions().getFirst().comment());
    }

    private ApprovalRequest openSingle(String approverId) {
        return service.open(OpenApprovalCommand.singleStage(
                tenantId, ApprovalSubjectType.MEETING_NOTE_VERSION, UUID.randomUUID(), approverId, null
        ));
    }
}
