package com.nanobaseai.actenora.meetingintelligence.api.web;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
import java.util.UUID;

/**
 * Meeting intelligence HTTP surface.
 * Tenant is resolved from authenticated identity via TenantContextPort — never from body alone.
 */
@RestController
@RequestMapping("/api/v1")
public class MeetingIntelligenceController {

    private final MeetingIntelligenceApi api;

    public MeetingIntelligenceController(MeetingIntelligenceApi api) {
        this.api = api;
    }

    @GetMapping("/meeting-notes/{noteId}")
    public MeetingNoteDetailResponse noteDetail(@PathVariable UUID noteId) {
        return api.getNoteDetail(noteId);
    }

    @PutMapping("/meeting-notes/{noteId}")
    public MeetingNoteDetailResponse updateNote(
            @PathVariable UUID noteId,
            @RequestBody MeetingNoteUpdateRequest request
    ) {
        return api.updateNote(noteId, request);
    }

    @GetMapping("/meeting-notes/{noteId}/versions")
    public List<MeetingNoteVersionResponse> listVersions(@PathVariable UUID noteId) {
        return api.listVersions(noteId);
    }

    @GetMapping("/meeting-notes/{noteId}/versions/compare")
    public VersionCompareResponse compareVersions(
            @PathVariable UUID noteId,
            @RequestParam("from") int fromVersion,
            @RequestParam("to") int toVersion
    ) {
        return api.compareVersions(noteId, fromVersion, toVersion);
    }

    @PutMapping("/decisions/{decisionId}")
    public DecisionResponse updateDecision(
            @PathVariable UUID decisionId,
            @RequestBody DecisionUpdateRequest request
    ) {
        return api.updateDecision(decisionId, request);
    }

    @PutMapping("/action-items/{actionItemId}")
    public ActionItemResponse updateActionItem(
            @PathVariable UUID actionItemId,
            @RequestBody ActionItemUpdateRequest request
    ) {
        return api.updateActionItem(actionItemId, request);
    }

    @PutMapping("/risks/{riskId}")
    public RiskResponse updateRisk(
            @PathVariable UUID riskId,
            @RequestBody RiskUpdateRequest request
    ) {
        return api.updateRisk(riskId, request);
    }

    @PostMapping("/commitments/{commitmentId}/approve")
    public CommitmentResponse approveCommitment(
            @PathVariable UUID commitmentId,
            @RequestBody CommitmentDecisionRequest request
    ) {
        return api.approveCommitment(commitmentId, request);
    }

    @PostMapping("/commitments/{commitmentId}/reject")
    public CommitmentResponse rejectCommitment(
            @PathVariable UUID commitmentId,
            @RequestBody CommitmentDecisionRequest request
    ) {
        return api.rejectCommitment(commitmentId, request);
    }

    @GetMapping("/evidence/{evidenceId}")
    public EvidenceLinkResponse evidenceDetail(@PathVariable UUID evidenceId) {
        return api.getEvidenceDetail(evidenceId);
    }

    @ExceptionHandler(ActenoraException.class)
    public ProblemDetail handleActenora(ActenoraException ex) {
        HttpStatus status = switch (ex.code()) {
            case "MEETING_NOTE_NOT_FOUND", "INTELLIGENCE_RESOURCE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "TENANT_ISOLATION_VIOLATION" -> HttpStatus.FORBIDDEN;
            case "OPTIMISTIC_LOCK_CONFLICT" -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(ex.code());
        problem.setProperty("code", ex.code());
        return problem;
    }
}
