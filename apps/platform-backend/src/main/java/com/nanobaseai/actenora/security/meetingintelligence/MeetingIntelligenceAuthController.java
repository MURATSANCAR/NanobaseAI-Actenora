package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.api.RequiresPermission;
import com.nanobaseai.actenora.identity.api.Permission;
import com.nanobaseai.actenora.meetingintelligence.api.MeetingIntelligenceApi;
import com.nanobaseai.actenora.meetingintelligence.api.dto.ActionItemResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.ActionItemUpdateRequest;
import com.nanobaseai.actenora.meetingintelligence.api.dto.CommitmentDecisionRequest;
import com.nanobaseai.actenora.meetingintelligence.api.dto.CommitmentResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.DecisionResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.DecisionUpdateRequest;
import com.nanobaseai.actenora.meetingintelligence.api.dto.EvidenceLinkResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteDetailResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteUpdateRequest;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteVersionResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.RiskResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.RiskUpdateRequest;
import com.nanobaseai.actenora.meetingintelligence.api.dto.VersionCompareResponse;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Auth-bound Meeting Intelligence HTTP surface. Tenant comes from {@link TenantSecurityContext} only.
 */
@RestController
@RequestMapping("/api/v1")
public class MeetingIntelligenceAuthController {

    private final MeetingIntelligenceApi api;
    private final IdentityApi identityApi;

    public MeetingIntelligenceAuthController(MeetingIntelligenceApi api, IdentityApi identityApi) {
        this.api = Objects.requireNonNull(api, "api");
        this.identityApi = Objects.requireNonNull(identityApi, "identityApi");
    }

    @GetMapping("/meeting-notes/{noteId}")
    @RequiresPermission(Permission.MEETING_READ)
    public MeetingNoteDetailResponse noteDetail(@PathVariable UUID noteId) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_READ);
        MeetingNoteDetailResponse note = api.getNoteDetail(noteId);
        assertSameTenant(principal, note.tenantId());
        return note;
    }

    @PutMapping("/meeting-notes/{noteId}")
    @RequiresPermission(Permission.MEETING_WRITE)
    public MeetingNoteDetailResponse updateNote(
            @PathVariable UUID noteId,
            @RequestBody MeetingNoteUpdateRequest request
    ) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_WRITE);
        MeetingNoteDetailResponse existing = api.getNoteDetail(noteId);
        assertSameTenant(principal, existing.tenantId());
        return api.updateNote(noteId, request);
    }

    @GetMapping("/meeting-notes/{noteId}/versions")
    @RequiresPermission(Permission.MEETING_READ)
    public List<MeetingNoteVersionResponse> listVersions(@PathVariable UUID noteId) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_READ);
        MeetingNoteDetailResponse note = api.getNoteDetail(noteId);
        assertSameTenant(principal, note.tenantId());
        return api.listVersions(noteId);
    }

    @GetMapping("/meeting-notes/{noteId}/versions/compare")
    @RequiresPermission(Permission.MEETING_READ)
    public VersionCompareResponse compareVersions(
            @PathVariable UUID noteId,
            @RequestParam("from") int fromVersion,
            @RequestParam("to") int toVersion
    ) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_READ);
        MeetingNoteDetailResponse note = api.getNoteDetail(noteId);
        assertSameTenant(principal, note.tenantId());
        return api.compareVersions(noteId, fromVersion, toVersion);
    }

    @PutMapping("/decisions/{decisionId}")
    @RequiresPermission(Permission.MEETING_WRITE)
    public DecisionResponse updateDecision(
            @PathVariable UUID decisionId,
            @RequestBody DecisionUpdateRequest request
    ) {
        requireWrite();
        return api.updateDecision(decisionId, request);
    }

    @PutMapping("/action-items/{actionItemId}")
    @RequiresPermission(Permission.MEETING_WRITE)
    public ActionItemResponse updateActionItem(
            @PathVariable UUID actionItemId,
            @RequestBody ActionItemUpdateRequest request
    ) {
        requireWrite();
        return api.updateActionItem(actionItemId, request);
    }

    @PutMapping("/risks/{riskId}")
    @RequiresPermission(Permission.MEETING_WRITE)
    public RiskResponse updateRisk(
            @PathVariable UUID riskId,
            @RequestBody RiskUpdateRequest request
    ) {
        requireWrite();
        return api.updateRisk(riskId, request);
    }

    @PostMapping("/commitments/{commitmentId}/approve")
    @RequiresPermission(Permission.MEETING_WRITE)
    public CommitmentResponse approveCommitment(
            @PathVariable UUID commitmentId,
            @RequestBody CommitmentDecisionRequest request
    ) {
        requireWrite();
        return api.approveCommitment(commitmentId, request);
    }

    @PostMapping("/commitments/{commitmentId}/reject")
    @RequiresPermission(Permission.MEETING_WRITE)
    public CommitmentResponse rejectCommitment(
            @PathVariable UUID commitmentId,
            @RequestBody CommitmentDecisionRequest request
    ) {
        requireWrite();
        return api.rejectCommitment(commitmentId, request);
    }

    @GetMapping("/evidence/{evidenceId}")
    @RequiresPermission(Permission.MEETING_READ)
    public EvidenceLinkResponse evidenceDetail(@PathVariable UUID evidenceId) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_READ);
        return api.getEvidenceDetail(evidenceId);
    }

    @ExceptionHandler(ActenoraException.class)
    public ResponseEntity<ProblemDetail> handleActenora(ActenoraException ex) {
        HttpStatus status = switch (ex.code()) {
            case "MEETING_NOTE_NOT_FOUND", "INTELLIGENCE_RESOURCE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "TENANT_ISOLATION_VIOLATION" -> HttpStatus.FORBIDDEN;
            case "OPTIMISTIC_LOCK_CONFLICT" -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(ex.code());
        problem.setProperty("code", ex.code());
        return ResponseEntity.status(status).body(problem);
    }

    private void requireWrite() {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_WRITE);
    }

    private static void assertSameTenant(AuthenticatedPrincipal principal, UUID noteTenantId) {
        if (!principal.tenantId().value().equals(noteTenantId)) {
            throw new ActenoraException("TENANT_ISOLATION_VIOLATION", "Cross-tenant meeting note access denied");
        }
    }
}
