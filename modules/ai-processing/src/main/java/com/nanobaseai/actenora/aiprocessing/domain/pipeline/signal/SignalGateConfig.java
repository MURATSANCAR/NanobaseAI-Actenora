package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

/**
 * Runtime policy for adaptive chunk signal gate.
 * Thresholds are tunable; dictionaries are versioned helpers — never hard gates alone.
 */
public record SignalGateConfig(
        boolean enabled,
        String mode,
        double threshold,
        boolean continuationAware,
        boolean semanticRepetitionEnabled,
        boolean hardMarkerShortcutEnabled,
        boolean shadowMode,
        String policyVersion,
        String dictionaryVersion
) {
    public SignalGateConfig {
        if (threshold < 0.0d) {
            throw new IllegalArgumentException("threshold must be >= 0");
        }
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("policyVersion");
        }
        if (dictionaryVersion == null || dictionaryVersion.isBlank()) {
            throw new IllegalArgumentException("dictionaryVersion");
        }
        mode = mode == null || mode.isBlank() ? "adaptive" : mode;
    }

    /**
     * Production defaults: adaptive gate on, shadow mode on (measure FN before hard skip).
     */
    public static SignalGateConfig productionDefaults() {
        return new SignalGateConfig(
                true,
                "adaptive",
                4.5d,
                true,
                true,
                true,
                true,
                "adaptive-gate-v1",
                "tr-en-v1"
        );
    }

    public SignalGateConfig withShadowMode(boolean shadow) {
        return new SignalGateConfig(
                enabled, mode, threshold, continuationAware, semanticRepetitionEnabled,
                hardMarkerShortcutEnabled, shadow, policyVersion, dictionaryVersion
        );
    }

    public SignalGateConfig withEnabled(boolean on) {
        return new SignalGateConfig(
                on, mode, threshold, continuationAware, semanticRepetitionEnabled,
                hardMarkerShortcutEnabled, shadowMode, policyVersion, dictionaryVersion
        );
    }
}
