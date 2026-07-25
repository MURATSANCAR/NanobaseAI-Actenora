package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import java.util.List;
import java.util.Objects;

/**
 * Full structured extraction payload matching the published output schema.
 */
public record ExtractionBundle(
        List<TopicCandidate> topics,
        List<DecisionCandidate> decisions,
        List<ActionItemCandidate> actionItems,
        List<RiskCandidate> risks,
        List<OpenQuestionCandidate> openQuestions,
        List<CommitmentCandidate> commitments,
        List<String> qualityFlags,
        List<String> evidenceSegmentIds,
        double confidence
) {
    public ExtractionBundle {
        topics = List.copyOf(Objects.requireNonNull(topics, "topics"));
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

    public static ExtractionBundle empty() {
        return new ExtractionBundle(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), 0.0d
        );
    }
}
