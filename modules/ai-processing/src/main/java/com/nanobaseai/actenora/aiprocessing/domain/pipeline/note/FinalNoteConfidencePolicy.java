package com.nanobaseai.actenora.aiprocessing.domain.pipeline.note;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.MeetingQualityProperties;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency.CrossTypeConsistencyAuditor;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency.DecisionProposalSubsumer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Caps confidence and forces manual review when synthesis/audit fell back or conflicts are unresolved.
 * Successful consistency drops never force RMR by themselves.
 */
public final class FinalNoteConfidencePolicy {

    private final MeetingQualityProperties properties;

    public FinalNoteConfidencePolicy(MeetingQualityProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public static FinalNoteConfidencePolicy productionDefaults() {
        return new FinalNoteConfidencePolicy(MeetingQualityProperties.load());
    }

    public FinalNoteDraft apply(FinalNoteDraft draft) {
        Objects.requireNonNull(draft, "draft");
        boolean synthesisFallback = hasFlag(draft, "SYNTHESIS_FALLBACK");
        boolean auditFallback = hasFlag(draft, "AUDIT_FALLBACK");
        boolean unresolvedConflict = hasFlag(draft, DecisionProposalSubsumer.UNRESOLVED)
                || hasFlag(draft, CrossTypeConsistencyAuditor.AUDIT_NEEDS_REVIEW);

        double confidence = draft.confidence();
        if (synthesisFallback) {
            confidence = Math.min(confidence, properties.synthesisFallbackConfidenceCap());
        }
        if (auditFallback) {
            confidence = Math.min(confidence, properties.auditFallbackConfidenceCap());
        }
        if (synthesisFallback && auditFallback) {
            confidence = Math.min(confidence, properties.doubleFallbackConfidenceCap());
        }

        boolean fallbackReview = properties.manualReviewOnAnyFallback()
                && (synthesisFallback || auditFallback);
        boolean otherReviewSignal = hasFlag(draft, "NEEDS_REVIEW")
                || hasFlag(draft, "LOW_CONFIDENCE");

        boolean manual = unresolvedConflict || fallbackReview || otherReviewSignal;
        // Preserve prior RMR only when it is not solely from a successful consistency pass.
        if (draft.requiresManualReview() && !successfulConsistencyOnly(draft) && !manual) {
            manual = true;
        }

        List<String> flags = new ArrayList<>(draft.qualityFlags());
        if (manual) {
            if (flags.stream().noneMatch(f -> f != null && f.equalsIgnoreCase("REQUIRES_MANUAL_REVIEW"))) {
                flags.add("REQUIRES_MANUAL_REVIEW");
            }
        } else {
            flags.removeIf(f -> f != null && f.equalsIgnoreCase("REQUIRES_MANUAL_REVIEW"));
        }

        return new FinalNoteDraft(
                draft.executiveSummary(),
                draft.decisions(),
                draft.actionItems(),
                draft.risks(),
                draft.openQuestions(),
                draft.commitments(),
                draft.topics(),
                draft.issues(),
                draft.proposals(),
                draft.importantFacts(),
                flags,
                draft.evidenceSegmentIds(),
                confidence,
                manual
        );
    }

    private static boolean successfulConsistencyOnly(FinalNoteDraft draft) {
        // Only successful subsumption drops clear RMR — a bare PASSED audit does not.
        boolean dropped = hasFlag(draft, DecisionProposalSubsumer.DROPPED);
        boolean unresolved = hasFlag(draft, DecisionProposalSubsumer.UNRESOLVED)
                || hasFlag(draft, CrossTypeConsistencyAuditor.AUDIT_NEEDS_REVIEW);
        boolean fallback = hasFlag(draft, "SYNTHESIS_FALLBACK") || hasFlag(draft, "AUDIT_FALLBACK");
        return dropped && !unresolved && !fallback
                && !hasFlag(draft, "NEEDS_REVIEW")
                && !hasFlag(draft, "LOW_CONFIDENCE");
    }

    private static boolean hasFlag(FinalNoteDraft draft, String code) {
        String needle = code.toUpperCase(Locale.ROOT);
        return draft.qualityFlags().stream()
                .anyMatch(f -> f != null && f.toUpperCase(Locale.ROOT).contains(needle));
    }
}
