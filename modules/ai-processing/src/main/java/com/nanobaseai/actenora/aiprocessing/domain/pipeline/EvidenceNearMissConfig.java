package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

/**
 * Optional near-miss evidence ID correction. Default off — soft-drop is safer for the first pass.
 *
 * <pre>
 * evidence.near-miss-correction.enabled=false
 * evidence.near-miss-correction.max-distance=2
 * evidence.near-miss-correction.min-prefix-length=12
 * evidence.near-miss-correction.chunk-local-only=true
 * evidence.near-miss-correction.require-unique-candidate=true
 * </pre>
 */
public record EvidenceNearMissConfig(
        boolean enabled,
        int maxDistance,
        int minPrefixLength,
        boolean chunkLocalOnly,
        boolean requireUniqueCandidate
) {
    public EvidenceNearMissConfig {
        if (maxDistance < 0) {
            throw new IllegalArgumentException("maxDistance must be >= 0");
        }
        if (minPrefixLength < 1) {
            throw new IllegalArgumentException("minPrefixLength must be >= 1");
        }
    }

    public static EvidenceNearMissConfig disabled() {
        return new EvidenceNearMissConfig(false, 2, 12, true, true);
    }

    public static EvidenceNearMissConfig load() {
        EvidenceNearMissConfig defaults = disabled();
        return new EvidenceNearMissConfig(
                bool("evidence.near-miss-correction.enabled", defaults.enabled()),
                integer("evidence.near-miss-correction.max-distance", defaults.maxDistance()),
                integer("evidence.near-miss-correction.min-prefix-length", defaults.minPrefixLength()),
                bool("evidence.near-miss-correction.chunk-local-only", defaults.chunkLocalOnly()),
                bool("evidence.near-miss-correction.require-unique-candidate", defaults.requireUniqueCandidate())
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
        return Boolean.parseBoolean(str(key, Boolean.toString(fallback)));
    }

    private static int integer(String key, int fallback) {
        try {
            return Integer.parseInt(str(key, Integer.toString(fallback)));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
