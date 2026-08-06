package com.nanobaseai.actenora.meetingintelligence.api.dto;

import java.util.List;
import java.util.Objects;

/** Discussed topic (agenda) candidate accepted by Meeting Intelligence; mirrors ImportantFactCandidateInput. */
public record TopicCandidateInput(
        String text,
        List<String> evidenceSegmentIds,
        double confidence
) {
    public TopicCandidateInput {
        Objects.requireNonNull(text, "text");
        evidenceSegmentIds = List.copyOf(Objects.requireNonNull(evidenceSegmentIds, "evidenceSegmentIds"));
    }
}
