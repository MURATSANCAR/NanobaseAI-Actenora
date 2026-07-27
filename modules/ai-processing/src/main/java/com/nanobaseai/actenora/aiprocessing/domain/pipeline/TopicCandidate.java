package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import java.util.List;
import java.util.Objects;

public record TopicCandidate(
        String text,
        String summary,
        List<String> evidenceSegmentIds,
        double confidence
) {
    public TopicCandidate(String text, List<String> evidenceSegmentIds, double confidence) {
        this(text, null, evidenceSegmentIds, confidence);
    }

    public TopicCandidate {
        Objects.requireNonNull(text, "text");
        if (summary != null && summary.isBlank()) {
            summary = null;
        } else if (summary != null) {
            summary = summary.trim();
        }
        evidenceSegmentIds = List.copyOf(Objects.requireNonNull(evidenceSegmentIds, "evidenceSegmentIds"));
    }
}
