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

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Module-local HTTP façade helpers.
 *
 * <p>Production auth-bound endpoints live in the platform composition root
 * ({@code MeetingIntelligenceAuthController}). This class remains as a thin
 * programmatic surface for module tests that do not pull Spring MVC.
 */
public final class MeetingIntelligenceController {

    private final MeetingIntelligenceApi api;

    public MeetingIntelligenceController(MeetingIntelligenceApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    public MeetingNoteDetailResponse noteDetail(UUID noteId) {
        return api.getNoteDetail(noteId);
    }

    public MeetingNoteDetailResponse updateNote(UUID noteId, MeetingNoteUpdateRequest request) {
        return api.updateNote(noteId, request);
    }

    public List<MeetingNoteVersionResponse> listVersions(UUID noteId) {
        return api.listVersions(noteId);
    }

    public VersionCompareResponse compareVersions(UUID noteId, int fromVersion, int toVersion) {
        return api.compareVersions(noteId, fromVersion, toVersion);
    }

    public DecisionResponse updateDecision(UUID decisionId, DecisionUpdateRequest request) {
        return api.updateDecision(decisionId, request);
    }

    public ActionItemResponse updateActionItem(UUID actionItemId, ActionItemUpdateRequest request) {
        return api.updateActionItem(actionItemId, request);
    }

    public RiskResponse updateRisk(UUID riskId, RiskUpdateRequest request) {
        return api.updateRisk(riskId, request);
    }

    public CommitmentResponse approveCommitment(UUID commitmentId, CommitmentDecisionRequest request) {
        return api.approveCommitment(commitmentId, request);
    }

    public CommitmentResponse rejectCommitment(UUID commitmentId, CommitmentDecisionRequest request) {
        return api.rejectCommitment(commitmentId, request);
    }

    public EvidenceLinkResponse evidenceDetail(UUID evidenceId) {
        return api.getEvidenceDetail(evidenceId);
    }
}
