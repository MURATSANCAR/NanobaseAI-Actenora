package com.nanobaseai.actenora.transcript.domain.normalization;

/**
 * Algorithm identifier for idempotent re-runs.
 * Combined with dictionary revision into a full normalization version string.
 */
public final class NormalizationVersion {

    public static final String ALGORITHM = "transcript-normalize-v1";

    private NormalizationVersion() {
    }

    public static String compose(long dictionaryRevision) {
        if (dictionaryRevision < 1) {
            throw new IllegalArgumentException("dictionaryRevision must be >= 1");
        }
        return ALGORITHM + "/dict-r" + dictionaryRevision;
    }

    public static boolean isCompatible(String version) {
        return version != null && version.startsWith(ALGORITHM + "/");
    }
}
