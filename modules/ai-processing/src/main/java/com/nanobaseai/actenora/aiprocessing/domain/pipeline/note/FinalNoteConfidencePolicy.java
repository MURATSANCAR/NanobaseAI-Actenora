package com.nanobaseai.actenora.aiprocessing.domain.pipeline.note;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.MeetingQualityProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Caps confidence and forces manual review when synthesis/audit fell back.
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
        if (!synthesisFallback && !auditFallback) {
            return draft;
        }

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

        boolean manual = draft.requiresManualReview()
                || (properties.manualReviewOnAnyFallback() && (synthesisFallback || auditFallback));

        List<String> flags = new ArrayList<>(draft.qualityFlags());
        if (manual && flags.stream().noneMatch(f -> f.equalsIgnoreCase("REQUIRES_MANUAL_REVIEW"))) {
            flags.add("REQUIRES_MANUAL_REVIEW");
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

    private static boolean hasFlag(FinalNoteDraft draft, String code) {
        String needle = code.toUpperCase(Locale.ROOT);
        return draft.qualityFlags().stream()
                .anyMatch(f -> f != null && f.toUpperCase(Locale.ROOT).contains(needle));
    }
}
