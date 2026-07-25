package com.nanobaseai.actenora.microsoftconnection.domain.identity;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of resolving a Graph calendar event into identity + series semantics.
 */
public record SeriesOccurrenceResolution(
        ImmutableGraphEventIdentity immutableIdentity,
        SeriesOccurrenceKind kind,
        Optional<GraphOccurrenceContinuityKey> continuityKey
) {

    public SeriesOccurrenceResolution {
        Objects.requireNonNull(immutableIdentity, "immutableIdentity");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(continuityKey, "continuityKey");
    }

    public boolean isRecurringOccurrence() {
        return kind == SeriesOccurrenceKind.OCCURRENCE || kind == SeriesOccurrenceKind.EXCEPTION;
    }

    public boolean isSeriesMaster() {
        return kind == SeriesOccurrenceKind.SERIES_MASTER;
    }
}
