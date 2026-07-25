package com.nanobaseai.actenora.meeting.domain.continuity;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Continuity key for recurring occurrences.
 * Uses graphSeriesMasterId + iCalUId + originalStartAt — never join URL.
 */
public record OccurrenceContinuityKey(
        String graphSeriesMasterId,
        String iCalUId,
        Instant originalStartAt
) {

    public OccurrenceContinuityKey {
        Objects.requireNonNull(graphSeriesMasterId, "graphSeriesMasterId");
        Objects.requireNonNull(iCalUId, "iCalUId");
        Objects.requireNonNull(originalStartAt, "originalStartAt");
        if (graphSeriesMasterId.isBlank()) {
            throw new IllegalArgumentException("graphSeriesMasterId must not be blank");
        }
        if (iCalUId.isBlank()) {
            throw new IllegalArgumentException("iCalUId must not be blank");
        }
    }

    public boolean matches(OccurrenceContinuityKey other) {
        return equals(other);
    }

    public boolean sameSeries(OccurrenceContinuityKey other) {
        return graphSeriesMasterId.equals(other.graphSeriesMasterId)
                && iCalUId.equals(other.iCalUId);
    }

    public Optional<String> asCompositeKey() {
        return Optional.of(graphSeriesMasterId + "|" + iCalUId + "|" + originalStartAt);
    }
}
