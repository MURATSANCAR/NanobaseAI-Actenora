package com.nanobaseai.actenora.microsoftconnection.domain.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Continuity key for recurring Graph occurrences (never join URL).
 */
public record GraphOccurrenceContinuityKey(
        String graphSeriesMasterId,
        String iCalUId,
        Instant originalStartAt
) {

    public GraphOccurrenceContinuityKey {
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

    public boolean sameSeries(GraphOccurrenceContinuityKey other) {
        return graphSeriesMasterId.equals(other.graphSeriesMasterId)
                && iCalUId.equals(other.iCalUId);
    }

    public Optional<String> asCompositeKey() {
        return Optional.of(graphSeriesMasterId + "|" + iCalUId + "|" + originalStartAt);
    }
}
