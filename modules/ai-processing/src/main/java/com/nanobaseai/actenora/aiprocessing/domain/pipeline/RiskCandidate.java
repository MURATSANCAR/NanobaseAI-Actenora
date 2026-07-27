package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import java.util.List;
import java.util.Objects;

public record RiskCandidate(
        String text,
        List<String> evidenceSegmentIds,
        double confidence,
        String likelihood,
        String mitigation
) {
    public RiskCandidate(String text, List<String> evidenceSegmentIds, double confidence) {
        this(text, evidenceSegmentIds, confidence, null, null);
    }

    public RiskCandidate {
        Objects.requireNonNull(text, "text");
        evidenceSegmentIds = List.copyOf(Objects.requireNonNull(evidenceSegmentIds, "evidenceSegmentIds"));
    }
}
