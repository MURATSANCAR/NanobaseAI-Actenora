package com.nanobaseai.actenora.microsoftconnection.domain.identity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphSeriesResolverTest {

    private final GraphSeriesResolver resolver = new GraphSeriesResolver();

    @Test
    void occurrenceUsesContinuityKeyNotJoinUrl() {
        SeriesOccurrenceResolution result = resolver.resolve(
                "occ-9",
                "ical-9",
                "master-9",
                "occurrence",
                Instant.parse("2026-07-22T09:00:00Z"),
                Instant.parse("2026-07-22T09:00:00Z"),
                "https://teams.microsoft.com/l/meetup-join/IGNORED"
        );
        assertTrue(result.isRecurringOccurrence());
        assertEquals("occ-9", result.immutableIdentity().graphEventImmutableId());
        assertEquals("master-9", result.continuityKey().orElseThrow().graphSeriesMasterId());
    }

    @Test
    void seriesMasterResolved() {
        SeriesOccurrenceResolution result = resolver.resolve(
                "master-1",
                "ical-1",
                null,
                "seriesMaster",
                Instant.parse("2026-07-01T09:00:00Z"),
                null,
                null
        );
        assertTrue(result.isSeriesMaster());
        assertEquals(SeriesOccurrenceKind.SERIES_MASTER, result.kind());
    }
}
