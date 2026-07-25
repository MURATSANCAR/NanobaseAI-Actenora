package com.nanobaseai.actenora.meetingintelligence.api.dto;

import java.util.List;
import java.util.Objects;

public record ActionItemCandidateInput(
        String text,
        String owner,
        String dueDate,
        List<String> evidenceSegmentIds,
        double confidence
) {
    public ActionItemCandidateInput {
        Objects.requireNonNull(text, "text");
        evidenceSegmentIds = List.copyOf(Objects.requireNonNull(evidenceSegmentIds, "evidenceSegmentIds"));
    }
}
