package com.nanobaseai.actenora.meetingintelligence.api;

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
import java.util.UUID;

/**
 * Public façade for the Meeting Intelligence bounded context.
 * Cross-module callers use types in this package only.
 */
public interface MeetingIntelligenceApi {

    MeetingNoteDetailResponse mapAiCandidates(MapAiCandidatesCommand command);

    MeetingNoteDetailResponse getNoteDetail(UUID noteId);

    List<MeetingNoteDetailResponse> listNotesForMeeting(UUID meetingOccurrenceId);

    MeetingNoteDetailResponse updateNote(UUID noteId, MeetingNoteUpdateRequest request);

    List<MeetingNoteVersionResponse> listVersions(UUID noteId);

    VersionCompareResponse compareVersions(UUID noteId, int fromVersion, int toVersion);

    DecisionResponse updateDecision(UUID decisionId, DecisionUpdateRequest request);

    ActionItemResponse updateActionItem(UUID actionItemId, ActionItemUpdateRequest request);

    RiskResponse updateRisk(UUID riskId, RiskUpdateRequest request);

    CommitmentResponse approveCommitment(UUID commitmentId, CommitmentDecisionRequest request);

    CommitmentResponse rejectCommitment(UUID commitmentId, CommitmentDecisionRequest request);

    EvidenceLinkResponse getEvidenceDetail(UUID evidenceId);
}
