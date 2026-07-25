package com.nanobaseai.actenora.meetingintelligence.application;

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
import com.nanobaseai.actenora.meetingintelligence.api.dto.MapAiCandidatesCommand;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class MeetingIntelligenceApiFacade implements MeetingIntelligenceApi {

    private final MeetingIntelligenceApplicationService service;

    public MeetingIntelligenceApiFacade(MeetingIntelligenceApplicationService service) {
        this.service = Objects.requireNonNull(service);
    }

    @Override
    public MeetingNoteDetailResponse mapAiCandidates(MapAiCandidatesCommand command) {
        return service.mapAiCandidates(command);
    }

    @Override
    public MeetingNoteDetailResponse getNoteDetail(UUID noteId) {
        return service.noteDetail(noteId);
    }

    @Override
    public MeetingNoteDetailResponse updateNote(UUID noteId, MeetingNoteUpdateRequest request) {
        return service.updateNote(noteId, request);
    }

    @Override
    public List<MeetingNoteVersionResponse> listVersions(UUID noteId) {
        return service.listVersions(noteId);
    }

    @Override
    public VersionCompareResponse compareVersions(UUID noteId, int fromVersion, int toVersion) {
        return service.compareVersions(noteId, fromVersion, toVersion);
    }

    @Override
    public DecisionResponse updateDecision(UUID decisionId, DecisionUpdateRequest request) {
        return service.updateDecision(decisionId, request);
    }

    @Override
    public ActionItemResponse updateActionItem(UUID actionItemId, ActionItemUpdateRequest request) {
        return service.updateActionItem(actionItemId, request);
    }

    @Override
    public RiskResponse updateRisk(UUID riskId, RiskUpdateRequest request) {
        return service.updateRisk(riskId, request);
    }

    @Override
    public CommitmentResponse approveCommitment(UUID commitmentId, CommitmentDecisionRequest request) {
        return service.approveCommitment(commitmentId, request);
    }

    @Override
    public CommitmentResponse rejectCommitment(UUID commitmentId, CommitmentDecisionRequest request) {
        return service.rejectCommitment(commitmentId, request);
    }

    @Override
    public EvidenceLinkResponse getEvidenceDetail(UUID evidenceId) {
        return service.evidenceDetail(evidenceId);
    }
}
