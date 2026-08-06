package com.nanobaseai.actenora.aiprocessing.domain.pipeline.composer;

import java.util.List;
import java.util.Objects;

/**
 * Segment-addressable transcript digest. Free-text-only summaries are forbidden —
 * every signal/fact keeps evidenceSegmentIds through map-reduce.
 */
public record TranscriptDigest(
        List<DigestSignal> meetingSignals,
        List<DigestFact> candidateFacts,
        List<DigestFact> unresolvedQuestions,
        SegmentRange segmentRange
) {
    public TranscriptDigest {
        meetingSignals = List.copyOf(Objects.requireNonNull(meetingSignals, "meetingSignals"));
        candidateFacts = List.copyOf(Objects.requireNonNull(candidateFacts, "candidateFacts"));
        unresolvedQuestions = List.copyOf(Objects.requireNonNull(unresolvedQuestions, "unresolvedQuestions"));
        Objects.requireNonNull(segmentRange, "segmentRange");
    }

    public boolean isEmpty() {
        return meetingSignals.isEmpty() && candidateFacts.isEmpty() && unresolvedQuestions.isEmpty();
    }

    public record SegmentRange(int from, int to) {
        public SegmentRange {
            if (from < 0 || to < from) {
                throw new IllegalArgumentException("invalid segment range");
            }
        }
    }

    public record DigestSignal(String kind, String text, List<String> evidenceSegmentIds) {
        public DigestSignal {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(text, "text");
            evidenceSegmentIds = List.copyOf(Objects.requireNonNull(evidenceSegmentIds, "evidenceSegmentIds"));
        }
    }

    public record DigestFact(
            String kind,
            String text,
            String speaker,
            String temporalExpression,
            List<String> evidenceSegmentIds
    ) {
        public DigestFact {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(text, "text");
            evidenceSegmentIds = List.copyOf(Objects.requireNonNull(evidenceSegmentIds, "evidenceSegmentIds"));
        }
    }
}
