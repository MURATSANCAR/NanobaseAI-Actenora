package com.nanobaseai.actenora.meetingintelligence.api.dto;

import java.util.List;
import java.util.Objects;

public record RiskCandidateInput(
        String text,
        List<String> evidenceSegmentIds,
        double confidence,
        String likelihood,
        String mitigation
) {
    public RiskCandidateInput(String text, List<String> evidenceSegmentIds, double confidence) {
        this(text, evidenceSegmentIds, confidence, null, null);
    }

    public RiskCandidateInput {
        Objects.requireNonNull(text, "text");
        evidenceSegmentIds = List.copyOf(Objects.requireNonNull(evidenceSegmentIds, "evidenceSegmentIds"));
    }
}
