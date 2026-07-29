package com.nanobaseai.actenora.aiprocessing.domain.pipeline.action;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Restores only transcript lines explicitly labelled as action records.
 * The recovered parent still passes through decomposition, deduplication and audit.
 */
public final class ExplicitActionCueRecoverer {

    private final ActionDiscoursePrefixNormalizer prefixNormalizer = new ActionDiscoursePrefixNormalizer();
    private final CompoundActionDecomposer decomposer = new CompoundActionDecomposer();
    private final ActionIdentityNormalizer identity = new ActionIdentityNormalizer();

    public record Result(List<ActionItemCandidate> actions, int recovered) {
    }

    public Result recover(List<ActionItemCandidate> actions, List<SegmentInput> segments) {
        Objects.requireNonNull(actions, "actions");
        List<ActionItemCandidate> recovered = new ArrayList<>(actions);
        int count = 0;
        for (SegmentInput segment : segments == null ? List.<SegmentInput>of() : segments) {
            String content = segment.content();
            if (content == null || !prefixNormalizer.startsWithDiscoursePrefix(content)) {
                continue;
            }
            ActionItemCandidate parent = new ActionItemCandidate(
                    content.strip(),
                    null,
                    null,
                    List.of(segment.segmentId()),
                    0.85d,
                    "PERSON",
                    null,
                    null,
                    null
            );
            List<ActionItemCandidate> expectedChildren =
                    decomposer.decompose(parent, java.util.Set.of(), segments).actions();
            boolean alreadyCovered = expectedChildren.stream()
                    .allMatch(expected -> actions.stream().anyMatch(existing ->
                            identity.canonicalOwner(existing).equals(identity.canonicalOwner(expected))
                                    && existing.evidenceSegmentIds().contains(segment.segmentId())));
            if (alreadyCovered) {
                continue;
            }
            recovered.add(parent);
            count++;
        }
        return new Result(List.copyOf(recovered), count);
    }
}
