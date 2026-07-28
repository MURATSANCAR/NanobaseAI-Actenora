package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

/**
 * Config for meeting quality / fallback confidence and speech-act thresholds.
 *
 * <pre>
 * actenora.meeting.quality.synthesis-fallback-confidence-cap=0.65
 * actenora.meeting.quality.audit-fallback-confidence-cap=0.55
 * actenora.meeting.quality.double-fallback-confidence-cap=0.45
 * actenora.meeting.quality.manual-review-on-any-fallback=true
 * actenora.meeting.quality.semantic-apply-min-confidence=0.90
 * actenora.meeting.quality.semantic-review-min-confidence=0.65
 * actenora.meeting.quality.deterministic-apply-min-confidence=0.90
 * </pre>
 */
public record MeetingQualityProperties(
        double synthesisFallbackConfidenceCap,
        double auditFallbackConfidenceCap,
        double doubleFallbackConfidenceCap,
        boolean manualReviewOnAnyFallback,
        double semanticApplyMinConfidence,
        double semanticReviewMinConfidence,
        double deterministicApplyMinConfidence
) {
    public MeetingQualityProperties {
        requireUnit("synthesisFallbackConfidenceCap", synthesisFallbackConfidenceCap);
        requireUnit("auditFallbackConfidenceCap", auditFallbackConfidenceCap);
        requireUnit("doubleFallbackConfidenceCap", doubleFallbackConfidenceCap);
        requireUnit("semanticApplyMinConfidence", semanticApplyMinConfidence);
        requireUnit("semanticReviewMinConfidence", semanticReviewMinConfidence);
        requireUnit("deterministicApplyMinConfidence", deterministicApplyMinConfidence);
    }

    public static MeetingQualityProperties defaults() {
        return new MeetingQualityProperties(0.65d, 0.55d, 0.45d, true, 0.90d, 0.65d, 0.90d);
    }

    public static MeetingQualityProperties load() {
        MeetingQualityProperties d = defaults();
        return new MeetingQualityProperties(
                dbl("actenora.meeting.quality.synthesis-fallback-confidence-cap", d.synthesisFallbackConfidenceCap()),
                dbl("actenora.meeting.quality.audit-fallback-confidence-cap", d.auditFallbackConfidenceCap()),
                dbl("actenora.meeting.quality.double-fallback-confidence-cap", d.doubleFallbackConfidenceCap()),
                bool("actenora.meeting.quality.manual-review-on-any-fallback", d.manualReviewOnAnyFallback()),
                dbl("actenora.meeting.quality.semantic-apply-min-confidence", d.semanticApplyMinConfidence()),
                dbl("actenora.meeting.quality.semantic-review-min-confidence", d.semanticReviewMinConfidence()),
                dbl("actenora.meeting.quality.deterministic-apply-min-confidence", d.deterministicApplyMinConfidence())
        );
    }

    private static void requireUnit(String name, double value) {
        if (value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(name + " must be in [0,1]");
        }
    }

    private static String str(String key, String fallback) {
        String env = System.getenv(key.toUpperCase().replace('.', '_').replace('-', '_'));
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return System.getProperty(key, fallback);
    }

    private static boolean bool(String key, boolean fallback) {
        return Boolean.parseBoolean(str(key, Boolean.toString(fallback)));
    }

    private static double dbl(String key, double fallback) {
        try {
            return Double.parseDouble(str(key, Double.toString(fallback)));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
