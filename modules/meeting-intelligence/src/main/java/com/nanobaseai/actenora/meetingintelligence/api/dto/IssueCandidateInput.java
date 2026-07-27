package com.nanobaseai.actenora.meetingintelligence.api.dto;

import java.util.List;
import java.util.Objects;

public record IssueCandidateInput(
        String text,
        List<String> evidenceSegmentIds,
        double confidence
) {
    public IssueCandidateInput {
        Objects.requireNonNull(text, "text");
        evidenceSegmentIds = List.copyOf(Objects.requireNonNull(evidenceSegmentIds, "evidenceSegmentIds"));
    }
}
