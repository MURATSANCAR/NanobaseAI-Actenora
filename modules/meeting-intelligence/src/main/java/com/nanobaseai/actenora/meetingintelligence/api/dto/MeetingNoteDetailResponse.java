package com.nanobaseai.actenora.meetingintelligence.api.dto;

import com.nanobaseai.actenora.meetingintelligence.domain.model.NoteReviewStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MeetingNoteDetailResponse(
        UUID id,
        UUID tenantId,
        UUID meetingOccurrenceId,
        NoteReviewStatus reviewStatus,
        int currentVersionNumber,
        long version,
        MeetingNoteVersionResponse currentVersion,
        List<DecisionResponse> decisions,
        List<ActionItemResponse> actionItems,
        List<RiskResponse> risks,
        List<CommitmentResponse> commitments,
        List<OpenQuestionResponse> openQuestions,
        List<IssueResponse> issues,
        List<ProposalResponse> proposals,
        List<ImportantFactResponse> importantFacts,
        List<EvidenceLinkResponse> evidenceLinks,
        List<QualityFlagResponse> qualityFlags,
        Instant createdAt,
        Instant updatedAt
) {
}
