package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import java.util.Objects;

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
 *
 * Env aliases also accepted (match {@code application.yml}):
 * {@code ACTENORA_MEETING_QUALITY_SYNTHESIS_FALLBACK_CAP},
 * {@code ACTENORA_MEETING_QUALITY_AUDIT_FALLBACK_CAP},
 * {@code ACTENORA_MEETING_QUALITY_DOUBLE_FALLBACK_CAP},
 * {@code ACTENORA_MEETING_QUALITY_MANUAL_REVIEW_ON_FALLBACK}.
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
    private static volatile MeetingQualityProperties installed;

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

    /** Spring composition root installs YAML-bound values so static {@link #load()} stays consistent. */
    public static void install(MeetingQualityProperties properties) {
        installed = Objects.requireNonNull(properties, "properties");
    }

    public static void clearInstall() {
        installed = null;
    }

    public static MeetingQualityProperties load() {
        MeetingQualityProperties override = installed;
        if (override != null) {
            return override;
        }
        MeetingQualityProperties d = defaults();
        return new MeetingQualityProperties(
                dbl(
                        d.synthesisFallbackConfidenceCap(),
                        "actenora.meeting.quality.synthesis-fallback-confidence-cap",
                        "ACTENORA_MEETING_QUALITY_SYNTHESIS_FALLBACK_CAP"
                ),
                dbl(
                        d.auditFallbackConfidenceCap(),
                        "actenora.meeting.quality.audit-fallback-confidence-cap",
                        "ACTENORA_MEETING_QUALITY_AUDIT_FALLBACK_CAP"
                ),
                dbl(
                        d.doubleFallbackConfidenceCap(),
                        "actenora.meeting.quality.double-fallback-confidence-cap",
                        "ACTENORA_MEETING_QUALITY_DOUBLE_FALLBACK_CAP"
                ),
                bool(
                        d.manualReviewOnAnyFallback(),
                        "actenora.meeting.quality.manual-review-on-any-fallback",
                        "ACTENORA_MEETING_QUALITY_MANUAL_REVIEW_ON_FALLBACK"
                ),
                dbl(
                        d.semanticApplyMinConfidence(),
                        "actenora.meeting.quality.semantic-apply-min-confidence",
                        "ACTENORA_MEETING_QUALITY_SEMANTIC_APPLY_MIN_CONFIDENCE"
                ),
                dbl(
                        d.semanticReviewMinConfidence(),
                        "actenora.meeting.quality.semantic-review-min-confidence",
                        "ACTENORA_MEETING_QUALITY_SEMANTIC_REVIEW_MIN_CONFIDENCE"
                ),
                dbl(
                        d.deterministicApplyMinConfidence(),
                        "actenora.meeting.quality.deterministic-apply-min-confidence",
                        "ACTENORA_MEETING_QUALITY_DETERMINISTIC_APPLY_MIN_CONFIDENCE"
                )
        );
    }

    private static void requireUnit(String name, double value) {
        if (value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(name + " must be in [0,1]");
        }
    }

    private static String firstNonBlank(String... keys) {
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            String env = System.getenv(key);
            if (env != null && !env.isBlank()) {
                return env.trim();
            }
            String dottedEnv = System.getenv(key.toUpperCase().replace('.', '_').replace('-', '_'));
            if (dottedEnv != null && !dottedEnv.isBlank()) {
                return dottedEnv.trim();
            }
            String prop = System.getProperty(key);
            if (prop != null && !prop.isBlank()) {
                return prop.trim();
            }
        }
        return null;
    }

    private static boolean bool(boolean fallback, String... keys) {
        String v = firstNonBlank(keys);
        return v == null ? fallback : Boolean.parseBoolean(v);
    }

    private static double dbl(double fallback, String... keys) {
        String v = firstNonBlank(keys);
        if (v == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
