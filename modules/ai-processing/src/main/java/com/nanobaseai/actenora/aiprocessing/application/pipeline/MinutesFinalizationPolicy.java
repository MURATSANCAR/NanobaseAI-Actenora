package com.nanobaseai.actenora.aiprocessing.application.pipeline;

import java.util.Locale;
import java.util.Objects;

/**
 * Runtime policy for the final minutes model step.
 *
 * <p>Prompts, model contract identifiers, token budgets and failure behaviour are
 * supplied by deployment configuration. The Java implementation contains no
 * meeting-domain cue phrases or language-specific extraction rules.</p>
 */
public record MinutesFinalizationPolicy(
        Mode mode,
        String promptResource,
        String promptVersionId,
        String schemaVersion,
        String taskType,
        int maxOutputTokens,
        int timeoutSeconds,
        FailureMode failureMode
) {

    public enum Mode {
        FULL,
        EDITORIAL,
        DETERMINISTIC,
        /**
         * Global candidate composer + grounded union + verified renderer.
         * High evidence rejection → MANUAL_REVIEW (not EDITORIAL polish).
         * Other failures fall back to EDITORIAL with explicit audit reason.
         */
        COMPOSER;

        public static Mode parse(String value) {
            return valueOf(requireText(value, "mode").toUpperCase(Locale.ROOT));
        }
    }

    public enum FailureMode {
        DETERMINISTIC,
        FAIL;

        public static FailureMode parse(String value) {
            return valueOf(requireText(value, "failureMode").toUpperCase(Locale.ROOT));
        }
    }

    public MinutesFinalizationPolicy {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(failureMode, "failureMode");
        if (mode == Mode.EDITORIAL || mode == Mode.COMPOSER) {
            promptResource = requireText(promptResource, "promptResource");
            promptVersionId = requireText(promptVersionId, "promptVersionId");
            schemaVersion = requireText(schemaVersion, "schemaVersion");
            taskType = requireText(taskType, "taskType");
            if (maxOutputTokens <= 0) {
                throw new IllegalArgumentException("maxOutputTokens must be > 0");
            }
            if (timeoutSeconds <= 0) {
                throw new IllegalArgumentException("timeoutSeconds must be > 0");
            }
        } else if (timeoutSeconds < 0) {
            throw new IllegalArgumentException("timeoutSeconds must be >= 0");
        }
    }

    public static MinutesFinalizationPolicy compatibility() {
        return new MinutesFinalizationPolicy(
                Mode.FULL,
                null,
                null,
                null,
                null,
                0,
                0,
                FailureMode.DETERMINISTIC
        );
    }

    /** Editorial/renderer policy derived from a COMPOSER policy (same prompt contract). */
    public MinutesFinalizationPolicy asEditorial() {
        if (mode != Mode.COMPOSER && mode != Mode.EDITORIAL) {
            throw new IllegalStateException("asEditorial requires COMPOSER or EDITORIAL mode");
        }
        return new MinutesFinalizationPolicy(
                Mode.EDITORIAL,
                promptResource,
                promptVersionId,
                schemaVersion,
                taskType,
                maxOutputTokens,
                timeoutSeconds,
                failureMode
        );
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
