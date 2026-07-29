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
        String relativeDate,
        String dueAt
) {
    public ActionItemCandidate(
            String text,
            String owner,
            String dueDate,
            List<String> evidenceSegmentIds,
            double confidence
    ) {
        this(text, owner, dueDate, evidenceSegmentIds, confidence, null, null, null, null);
    }

    public ActionItemCandidate(
            String text,
            String owner,
            String dueDate,
            List<String> evidenceSegmentIds,
            double confidence,
            String ownerType,
            String priority,
            String relativeDate
    ) {
        this(text, owner, dueDate, evidenceSegmentIds, confidence, ownerType, priority, relativeDate, null);
    }

    public ActionItemCandidate {
        Objects.requireNonNull(text, "text");
        evidenceSegmentIds = List.copyOf(Objects.requireNonNull(evidenceSegmentIds, "evidenceSegmentIds"));
    }

    public ActionItemCandidate withText(String newText) {
        return new ActionItemCandidate(
                newText, owner, dueDate, evidenceSegmentIds, confidence, ownerType, priority, relativeDate, dueAt);
    }

    public ActionItemCandidate withOwner(String newOwner) {
        return new ActionItemCandidate(
                text, newOwner, dueDate, evidenceSegmentIds, confidence, ownerType, priority, relativeDate, dueAt);
    }

    public ActionItemCandidate withDates(String newDueDate, String newRelativeDate, String newDueAt) {
        return new ActionItemCandidate(
                text, owner, newDueDate, evidenceSegmentIds, confidence, ownerType, priority, newRelativeDate, newDueAt);
    }

    public ActionItemCandidate withEvidence(List<String> evidence) {
        return new ActionItemCandidate(
                text, owner, dueDate, evidence, confidence, ownerType, priority, relativeDate, dueAt);
    }
}
