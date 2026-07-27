package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

/**
 * Loads {@link SignalGateConfig} from optional system properties / env for ops tuning
 * without requiring Spring in unit tests.
 *
 * <pre>
 * meeting.signal-gate.shadow-mode=false
 * meeting.signal-gate.threshold=4.5
 * meeting.speech-signals.dictionary-version=tr-en-v1
 * </pre>
 */
public final class SignalGateConfigLoader {

    private SignalGateConfigLoader() {
    }

    public static SignalGateConfig load() {
        SignalGateConfig defaults = SignalGateConfig.productionDefaults();
        boolean enabled = bool("meeting.signal-gate.enabled", defaults.enabled());
        String mode = str("meeting.signal-gate.mode", defaults.mode());
        double threshold = dbl("meeting.signal-gate.threshold", defaults.threshold());
        boolean continuation = bool("meeting.signal-gate.continuation-aware", defaults.continuationAware());
        boolean semantic = bool("meeting.signal-gate.semantic-repetition-enabled", defaults.semanticRepetitionEnabled());
        boolean hardMarker = bool("meeting.signal-gate.hard-marker-shortcut-enabled", defaults.hardMarkerShortcutEnabled());
        boolean shadow = bool("meeting.signal-gate.shadow-mode", defaults.shadowMode());
        String policy = str("meeting.signal-gate.policy-version", defaults.policyVersion());
        String dictionary = str("meeting.speech-signals.dictionary-version", defaults.dictionaryVersion());
        return new SignalGateConfig(
                enabled, mode, threshold, continuation, semantic, hardMarker, shadow, policy, dictionary
        );
    }

    private static String str(String key, String fallback) {
        String env = System.getenv(key.toUpperCase().replace('.', '_').replace('-', '_'));
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return System.getProperty(key, fallback);
    }

    private static boolean bool(String key, boolean fallback) {
        String v = str(key, Boolean.toString(fallback));
        return Boolean.parseBoolean(v);
    }

    private static double dbl(String key, double fallback) {
        String v = str(key, Double.toString(fallback));
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
