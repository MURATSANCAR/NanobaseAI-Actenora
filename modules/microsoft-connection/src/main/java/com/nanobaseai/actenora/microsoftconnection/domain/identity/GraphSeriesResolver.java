package com.nanobaseai.actenora.microsoftconnection.domain.identity;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves Graph calendar payload fields into immutable identity + series/occurrence kind.
 * Join URL is accepted only as ignored metadata.
 */
public final class GraphSeriesResolver {

    public SeriesOccurrenceResolution resolve(
            String eventId,
            String iCalUId,
            String seriesMasterId,
            String type,
            Instant startAt,
            Instant originalStartAt,
            String joinWebUrlIgnored
    ) {
        Objects.requireNonNull(eventId, "eventId");
        // joinWebUrlIgnored is accepted so call sites make the "not identity" rule explicit.
        SeriesOccurrenceKind kind = mapKind(type, seriesMasterId);
        ImmutableGraphEventIdentity identity = ImmutableGraphEventIdentity.of(eventId);
        GraphOccurrenceContinuityKey continuity = null;
        if (kind == SeriesOccurrenceKind.OCCURRENCE || kind == SeriesOccurrenceKind.EXCEPTION) {
            String master = requireNonBlank(seriesMasterId, "seriesMasterId");
            String ical = requireNonBlank(iCalUId, "iCalUId");
            Instant original = originalStartAt != null ? originalStartAt : startAt;
            if (original == null) {
                throw new IllegalArgumentException("originalStartAt or startAt required for occurrence");
            }
            continuity = new GraphOccurrenceContinuityKey(master, ical, original);
        } else if (kind == SeriesOccurrenceKind.SERIES_MASTER) {
            String ical = iCalUId == null || iCalUId.isBlank() ? eventId : iCalUId;
            Instant original = originalStartAt != null ? originalStartAt : startAt;
            if (original != null) {
                continuity = new GraphOccurrenceContinuityKey(eventId, ical, original);
            }
        }
        return new SeriesOccurrenceResolution(identity, kind, Optional.ofNullable(continuity));
    }

    private static SeriesOccurrenceKind mapKind(String type, String seriesMasterId) {
        if (type == null || type.isBlank()) {
            return seriesMasterId == null || seriesMasterId.isBlank()
                    ? SeriesOccurrenceKind.SINGLE
                    : SeriesOccurrenceKind.OCCURRENCE;
        }
        return switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "seriesmaster" -> SeriesOccurrenceKind.SERIES_MASTER;
            case "occurrence" -> SeriesOccurrenceKind.OCCURRENCE;
            case "exception" -> SeriesOccurrenceKind.EXCEPTION;
            default -> SeriesOccurrenceKind.SINGLE;
        };
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
