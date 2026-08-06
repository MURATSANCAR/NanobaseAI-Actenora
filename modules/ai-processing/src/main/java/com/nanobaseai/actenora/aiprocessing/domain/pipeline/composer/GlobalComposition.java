package com.nanobaseai.actenora.aiprocessing.domain.pipeline.composer;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Composer output: meeting frame + typed candidates. Does not contain final user-facing prose
 * beyond the evidence-backed frame text.
 */
public record GlobalComposition(
        MeetingFrame meetingFrame,
        List<GlobalCandidate> candidates
) {
    public GlobalComposition {
        candidates = List.copyOf(Objects.requireNonNullElse(candidates, List.of()));
    }

    public record MeetingFrame(
            String kind,
            String text,
            List<String> evidenceSegmentIds,
            double confidence
    ) {
        public MeetingFrame {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(text, "text");
            evidenceSegmentIds = List.copyOf(Objects.requireNonNull(evidenceSegmentIds, "evidenceSegmentIds"));
        }
    }

    public record GlobalCandidate(
            CandidateType type,
            String text,
            String ownerCandidate,
            String dueDateText,
            String dueDateNormalized,
            String mitigation,
            List<String> evidenceSegmentIds,
            String source,
            double confidence
    ) {
        public GlobalCandidate {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(text, "text");
            evidenceSegmentIds = List.copyOf(Objects.requireNonNull(evidenceSegmentIds, "evidenceSegmentIds"));
            source = source == null || source.isBlank() ? "DIGEST" : source;
            if (mitigation != null && mitigation.isBlank()) {
                mitigation = null;
            }
        }

        /** Compatibility constructor without mitigation. */
        public GlobalCandidate(
                CandidateType type,
                String text,
                String ownerCandidate,
                String dueDateText,
                String dueDateNormalized,
                List<String> evidenceSegmentIds,
                String source,
                double confidence
        ) {
            this(type, text, ownerCandidate, dueDateText, dueDateNormalized, null,
                    evidenceSegmentIds, source, confidence);
        }
    }

    public enum CandidateType {
        DECISION,
        ACTION,
        COMMITMENT,
        RISK,
        OPEN_QUESTION,
        IMPORTANT_FACT,
        PROPOSAL;

        public static CandidateType parse(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("candidate type required");
            }
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        }
    }
}
