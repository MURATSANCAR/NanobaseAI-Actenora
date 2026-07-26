package com.nanobaseai.actenora.security.approval;

import com.nanobaseai.actenora.approval.api.ApprovalApi;
import com.nanobaseai.actenora.approval.api.ApprovalDecisionType;
import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.approval.api.ApprovalRequestStatus;
import com.nanobaseai.actenora.approval.api.ApprovalRequestView;
import com.nanobaseai.actenora.approval.api.ApprovalSubjectType;
import com.nanobaseai.actenora.approval.domain.exception.OptimisticLockConflictException;
import com.nanobaseai.actenora.approval.domain.exception.UnauthorizedApprovalException;
import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.api.RequiresPermission;
import com.nanobaseai.actenora.identity.domain.Permission;
import com.nanobaseai.actenora.meetingintelligence.api.MeetingIntelligenceApi;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteDetailResponse;
import com.nanobaseai.actenora.meetingintelligence.application.MeetingNoteApprovalService;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNote;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Auth-bound approval HTTP surface (FAZ 18).
 * Tenant and actor identity come only from {@link TenantSecurityContext}.
 */
@RestController
@RequestMapping("/api/v1")
public class ApprovalAuthController {

    private final MeetingNoteApprovalService noteApprovalService;
    private final ApprovalApi approvalApi;
    private final MeetingIntelligenceApi meetingIntelligenceApi;
    private final IdentityApi identityApi;

    public ApprovalAuthController(
            MeetingNoteApprovalService noteApprovalService,
            ApprovalApi approvalApi,
            MeetingIntelligenceApi meetingIntelligenceApi,
            IdentityApi identityApi
    ) {
        this.noteApprovalService = Objects.requireNonNull(noteApprovalService, "noteApprovalService");
        this.approvalApi = Objects.requireNonNull(approvalApi, "approvalApi");
        this.meetingIntelligenceApi = Objects.requireNonNull(meetingIntelligenceApi, "meetingIntelligenceApi");
        this.identityApi = Objects.requireNonNull(identityApi, "identityApi");
    }

    @PostMapping("/meeting-notes/{noteId}/submit-for-approval")
    @RequiresPermission(Permission.MEETING_WRITE)
    public SubmitApprovalView submitForApproval(
            @PathVariable UUID noteId,
            @RequestBody SubmitApprovalRequest body
    ) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_WRITE);
        MeetingNoteDetailResponse note = meetingIntelligenceApi.getNoteDetail(noteId);
        assertSameTenant(principal, note.tenantId());

        String approverId = body.approverId() == null || body.approverId().isBlank()
                ? principal.userId().toString()
                : body.approverId().trim();
        long expectedVersion = body.expectedVersion() == null ? note.version() : body.expectedVersion();
        ApprovalId approvalId = noteApprovalService.submitForApproval(
                principal.tenantId().value(),
                noteId,
                approverId,
                body.expiresAt(),
                expectedVersion
        );
        ApprovalRequestView view = approvalApi.get(principal.tenantId().value(), approvalId).orElseThrow();
        return SubmitApprovalView.from(view, noteId);
    }

    @GetMapping("/approvals/{approvalId}")
    @RequiresPermission(Permission.MEETING_READ)
    public ApprovalRecordView getApproval(@PathVariable UUID approvalId) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_READ);
        ApprovalRequestView view = approvalApi.get(principal.tenantId().value(), ApprovalId.of(approvalId))
                .orElseThrow(() -> new ActenoraException(
                        "INTELLIGENCE_RESOURCE_NOT_FOUND",
                        "Approval not found: " + approvalId
                ));
        return ApprovalRecordView.from(view);
    }

    @PostMapping("/approvals/{approvalId}/decide")
    @RequiresPermission(Permission.APPROVAL_DECIDE)
    public ApprovalRecordView decide(
            @PathVariable UUID approvalId,
            @RequestBody DecideApprovalRequest body
    ) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.APPROVAL_DECIDE);
        ApprovalDecisionType decisionType = parseDecision(body.decision());
        MeetingNote note = noteApprovalService.decideByApprovalId(
                principal.tenantId().value(),
                ApprovalId.of(approvalId),
                principal.userId().toString(),
                decisionType,
                body.comment(),
                body.expectedNoteVersion(),
                body.expectedApprovalVersion()
        );
        ApprovalRequestView view = approvalApi.get(principal.tenantId().value(), ApprovalId.of(approvalId))
                .orElseThrow();
        return ApprovalRecordView.from(view, note.id());
    }

    @ExceptionHandler({
            UnauthorizedApprovalException.class,
            OptimisticLockConflictException.class,
            ActenoraException.class
    })
    public ResponseEntity<ProblemDetail> handleDomain(RuntimeException ex) {
        String code = ex instanceof ActenoraException actenora ? actenora.code() : "APPROVAL_ERROR";
        if (ex instanceof UnauthorizedApprovalException) {
            code = "UNAUTHORIZED_APPROVAL";
        } else if (ex instanceof OptimisticLockConflictException) {
            code = "OPTIMISTIC_LOCK_CONFLICT";
        }
        HttpStatus status = switch (code) {
            case "UNAUTHORIZED_APPROVAL", "TENANT_ISOLATION_VIOLATION" -> HttpStatus.FORBIDDEN;
            case "INTELLIGENCE_RESOURCE_NOT_FOUND", "MEETING_NOTE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "OPTIMISTIC_LOCK_CONFLICT" -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(code);
        problem.setProperty("code", code);
        return ResponseEntity.status(status).body(problem);
    }

    private static ApprovalDecisionType parseDecision(String decision) {
        if (decision == null || decision.isBlank()) {
            throw new ActenoraException("INVALID_APPROVAL_DECISION", "decision is required");
        }
        return switch (decision.trim().toUpperCase()) {
            case "APPROVE", "APPROVED", "GRANTED" -> ApprovalDecisionType.APPROVE;
            case "REJECT", "REJECTED", "DENIED" -> ApprovalDecisionType.REJECT;
            case "REQUEST_CHANGES", "CHANGES_REQUESTED" -> ApprovalDecisionType.REQUEST_CHANGES;
            default -> throw new ActenoraException(
                    "INVALID_APPROVAL_DECISION",
                    "Unsupported decision: " + decision
            );
        };
    }

    private static void assertSameTenant(AuthenticatedPrincipal principal, UUID noteTenantId) {
        if (!principal.tenantId().value().equals(noteTenantId)) {
            throw new ActenoraException("TENANT_ISOLATION_VIOLATION", "Tenant isolation violation");
        }
    }

    public record SubmitApprovalRequest(String approverId, Instant expiresAt, Long expectedVersion) {
    }

    public record DecideApprovalRequest(
            String decision,
            String comment,
            Long expectedNoteVersion,
            Long expectedApprovalVersion
    ) {
    }

    public record SubmitApprovalView(
            UUID approvalId,
            UUID noteId,
            UUID subjectId,
            ApprovalSubjectType subjectType,
            ApprovalRequestStatus status,
            long version
    ) {
        static SubmitApprovalView from(ApprovalRequestView view, UUID noteId) {
            return new SubmitApprovalView(
                    view.id().value(),
                    noteId,
                    view.subjectId(),
                    view.subjectType(),
                    view.status(),
                    view.version()
            );
        }
    }

    public record ApprovalRecordView(
            UUID id,
            String artifactType,
            UUID artifactId,
            UUID noteId,
            ApprovalRequestStatus status,
            long version,
            Instant updatedAt
    ) {
        static ApprovalRecordView from(ApprovalRequestView view) {
            return from(view, null);
        }

        static ApprovalRecordView from(ApprovalRequestView view, UUID noteId) {
            return new ApprovalRecordView(
                    view.id().value(),
                    view.subjectType().name(),
                    view.subjectId(),
                    noteId,
                    view.status(),
                    view.version(),
                    view.updatedAt()
            );
        }
    }
}
