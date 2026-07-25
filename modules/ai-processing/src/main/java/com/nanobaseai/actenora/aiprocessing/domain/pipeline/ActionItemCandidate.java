package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import java.util.List;
import java.util.Objects;

public record ActionItemCandidate(
        String text,
        String owner,
        String dueDate,
        List<String> evidenceSegmentIds,
        double confidence
) {
    public ActionItemCandidate {
        Objects.requireNonNull(text, "text");
        evidenceSegmentIds = List.copyOf(Objects.requireNonNull(evidenceSegmentIds, "evidenceSegmentIds"));
        // owner / dueDate may be null when uncertain (prompt rule)
    }
}
