package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import java.util.List;
import java.util.Objects;

public record OpenQuestionCandidate(
        String text,
        List<String> evidenceSegmentIds,
        double confidence
) {
    public OpenQuestionCandidate {
        Objects.requireNonNull(text, "text");
        evidenceSegmentIds = List.copyOf(Objects.requireNonNull(evidenceSegmentIds, "evidenceSegmentIds"));
    }
}
