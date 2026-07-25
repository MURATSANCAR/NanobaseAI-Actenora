package com.nanobaseai.actenora.transcript.domain.normalization;

import java.util.Objects;
import java.util.UUID;

/**
 * Algorithm identifier for idempotent re-runs.
 * Combined with dictionary id + revision into a full normalization version string.
 */
public final class NormalizationVersion {

    public static final String ALGORITHM = "transcript-normalize-v1";

    private NormalizationVersion() {
    }

    public static String compose(UUID dictionaryId, long dictionaryRevision) {
        Objects.requireNonNull(dictionaryId, "dictionaryId");
        if (dictionaryRevision < 1) {
            throw new IllegalArgumentException("dictionaryRevision must be >= 1");
        }
        return ALGORITHM + "/dict-" + dictionaryId + "-r" + dictionaryRevision;
    }

    /** @deprecated Prefer {@link #compose(UUID, long)} — revision alone collides across dictionaries. */
    @Deprecated
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
