package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import java.util.List;
import java.util.Objects;

/**
 * Final note draft produced after deterministic validation (not a persisted entity).
 */
public record FinalNoteDraft(
        String executiveSummary,
        List<DecisionCandidate> decisions,
        List<ActionItemCandidate> actionItems,
        List<RiskCandidate> risks,
        List<OpenQuestionCandidate> openQuestions,
        List<CommitmentCandidate> commitments,
        List<TopicCandidate> topics,
        List<String> qualityFlags,
        List<String> evidenceSegmentIds,
        double confidence,
        boolean requiresManualReview
) {
    public FinalNoteDraft {
        Objects.requireNonNull(executiveSummary, "executiveSummary");
        decisions = List.copyOf(Objects.requireNonNull(decisions, "decisions"));
        actionItems = List.copyOf(Objects.requireNonNull(actionItems, "actionItems"));
        risks = List.copyOf(Objects.requireNonNull(risks, "risks"));
        openQuestions = List.copyOf(Objects.requireNonNull(openQuestions, "openQuestions"));
        commitments = List.copyOf(Objects.requireNonNull(commitments, "commitments"));
        topics = List.copyOf(Objects.requireNonNull(topics, "topics"));
        qualityFlags = List.copyOf(Objects.requireNonNull(qualityFlags, "qualityFlags"));
        evidenceSegmentIds = List.copyOf(Objects.requireNonNull(evidenceSegmentIds, "evidenceSegmentIds"));
    }
}
