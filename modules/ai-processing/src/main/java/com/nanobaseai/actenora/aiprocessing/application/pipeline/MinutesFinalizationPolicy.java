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
        String promptId,
        String schemaVersion,
        String taskType,
        int maxOutputTokens,
        FailureMode failureMode
) {

    public enum Mode {
        FULL,
        EDITORIAL,
        DETERMINISTIC;

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
        if (mode == Mode.EDITORIAL) {
            promptResource = requireText(promptResource, "promptResource");
            promptVersionId = requireText(promptVersionId, "promptVersionId");
            promptId = requireText(promptId, "promptId");
            schemaVersion = requireText(schemaVersion, "schemaVersion");
            taskType = requireText(taskType, "taskType");
            if (maxOutputTokens <= 0) {
                throw new IllegalArgumentException("maxOutputTokens must be > 0");
            }
        }
    }

    /**
     * Compatibility policy for direct callers that have not opted into the
     * deployment-configured finalization path.
     */
    public static MinutesFinalizationPolicy compatibility() {
        return new MinutesFinalizationPolicy(
                Mode.FULL,
                null,
                null,
                null,
                null,
                null,
                0,
                FailureMode.DETERMINISTIC
        );
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
