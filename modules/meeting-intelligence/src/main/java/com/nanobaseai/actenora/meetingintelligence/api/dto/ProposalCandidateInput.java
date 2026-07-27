package com.nanobaseai.actenora.meetingintelligence.api.dto;

import java.util.List;
import java.util.Objects;

public record ProposalCandidateInput(
        String text,
        List<String> evidenceSegmentIds,
        double confidence
) {
    public ProposalCandidateInput {
        Objects.requireNonNull(text, "text");
        evidenceSegmentIds = List.copyOf(Objects.requireNonNull(evidenceSegmentIds, "evidenceSegmentIds"));
    }
}
