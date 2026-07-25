package com.nanobaseai.actenora.meetingintelligence.api.dto;

import java.util.List;
import java.util.Objects;

/**
 * AI candidate payload accepted by Meeting Intelligence.
 * AI responses are never persisted as entities; {@code MapAiCandidatesToNoteService}
 * maps this DTO into corporate business objects.
 */
public record AiCandidateBundle(
        String executiveSummary,
        List<DecisionCandidateInput> decisions,
        List<ActionItemCandidateInput> actionItems,
        List<RiskCandidateInput> risks,
        List<OpenQuestionCandidateInput> openQuestions,
        List<CommitmentCandidateInput> commitments,
        List<String> qualityFlags,
        List<String> evidenceSegmentIds,
        double confidence
) {
    public AiCandidateBundle {
        Objects.requireNonNull(executiveSummary, "executiveSummary");
        decisions = List.copyOf(Objects.requireNonNull(decisions, "decisions"));
        actionItems = List.copyOf(Objects.requireNonNull(actionItems, "actionItems"));
        risks = List.copyOf(Objects.requireNonNull(risks, "risks"));
        openQuestions = List.copyOf(Objects.requireNonNull(openQuestions, "openQuestions"));
        commitments = List.copyOf(Objects.requireNonNull(commitments, "commitments"));
        qualityFlags = List.copyOf(Objects.requireNonNull(qualityFlags, "qualityFlags"));
        evidenceSegmentIds = List.copyOf(Objects.requireNonNull(evidenceSegmentIds, "evidenceSegmentIds"));
        if (confidence < 0.0d || confidence > 1.0d) {
            throw new IllegalArgumentException("confidence must be in [0,1]");
        }
    }
}
