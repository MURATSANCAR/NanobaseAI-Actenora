package com.nanobaseai.actenora.meetingintelligence.api.dto;

import java.util.List;
import java.util.Objects;

public record ActionItemCandidateInput(
        String text,
        String owner,
        String dueDate,
        List<String> evidenceSegmentIds,
        double confidence,
        String ownerType,
        String priority,
        String relativeDate
) {
    public ActionItemCandidateInput(
            String text,
            String owner,
            String dueDate,
            List<String> evidenceSegmentIds,
            double confidence
    ) {
        this(text, owner, dueDate, evidenceSegmentIds, confidence, null, null, null);
    }

    public ActionItemCandidateInput {
        Objects.requireNonNull(text, "text");
        evidenceSegmentIds = List.copyOf(Objects.requireNonNull(evidenceSegmentIds, "evidenceSegmentIds"));
    }
}
