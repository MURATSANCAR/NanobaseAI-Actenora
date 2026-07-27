package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import java.util.List;
import java.util.Objects;

public record ActionItemCandidate(
        String text,
        String owner,
        String dueDate,
        List<String> evidenceSegmentIds,
        double confidence,
        String ownerType,
        String priority,
        String relativeDate
) {
    public ActionItemCandidate(
            String text,
            String owner,
            String dueDate,
            List<String> evidenceSegmentIds,
            double confidence
    ) {
        this(text, owner, dueDate, evidenceSegmentIds, confidence, null, null, null);
    }

    public ActionItemCandidate {
        Objects.requireNonNull(text, "text");
        evidenceSegmentIds = List.copyOf(Objects.requireNonNull(evidenceSegmentIds, "evidenceSegmentIds"));
    }
}
