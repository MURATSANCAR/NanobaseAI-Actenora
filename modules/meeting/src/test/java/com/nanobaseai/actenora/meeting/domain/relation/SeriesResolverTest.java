package com.nanobaseai.actenora.meeting.domain.relation;

import com.nanobaseai.actenora.meeting.domain.continuity.ImmutableEventIdentity;
import com.nanobaseai.actenora.meeting.domain.continuity.OccurrenceContinuityKey;
import com.nanobaseai.actenora.meeting.domain.continuity.OccurrenceIdentitySnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeriesResolverTest {

    private final SeriesResolver resolver = new SeriesResolver();
    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private final String joinUrl = "https://teams.microsoft.com/l/meetup-join/SAME_URL";

    @Test
    void recurringOccurrenceMappingUsesContinuityKey() {
        OccurrenceContinuityKey key = key("master-1", "ical-1", "2026-07-08T09:00:00Z");
        OccurrenceIdentitySnapshot occurrence = snapshot(
                tenantA,
                "imm-1",
                key,
                joinUrl,
                "Standup",
                "2026-07-08T09:00:00Z"
        );

        SeriesResolutionResult result = resolver.resolve(
                tenantA,
                ImmutableEventIdentity.of("imm-1"),
                key,
                "https://teams.microsoft.com/l/meetup-join/OTHER",
                List.of(occurrence)
        );

        assertEquals(SeriesResolutionResult.ResolutionStatus.MATCHED_OCCURRENCE, result.status());
        assertEquals(occurrence.occurrenceId(), result.matchedOccurrenceId().orElseThrow());
    }

    @Test
    void movedOccurrenceKeepsOriginalStartAtIdentity() {
        OccurrenceContinuityKey key = key("master-1", "ical-1", "2026-07-08T09:00:00Z");
        OccurrenceIdentitySnapshot original = snapshot(
                tenantA,
                "imm-moved",
                key,
                joinUrl,
                "Standup",
                "2026-07-08T09:00:00Z"
        );
        OccurrenceIdentitySnapshot moved = resolver.mapMovedOccurrence(
                original,
                Instant.parse("2026-07-08T11:00:00Z"),
                Instant.parse("2026-07-08T12:00:00Z")
        ).orElseThrow();

        assertEquals(key, moved.continuityKey());
        assertEquals(Instant.parse("2026-07-08T11:00:00Z"), moved.scheduledStartAt());

        SeriesResolutionResult result = resolver.resolve(
                tenantA,
                ImmutableEventIdentity.of("imm-moved"),
                key,
                joinUrl,
                List.of(moved)
        );
        assertEquals(SeriesResolutionResult.ResolutionStatus.MATCHED_OCCURRENCE, result.status());
        assertEquals(moved.occurrenceId(), result.matchedOccurrenceId().orElseThrow());
    }

    @Test
    void sameJoinUrlWithDifferentOccurrencesDoesNotCollide() {
        OccurrenceIdentitySnapshot first = snapshot(
                tenantA,
                "imm-a",
                key("master-1", "ical-1", "2026-07-01T10:00:00Z"),
                joinUrl,
                "Occ A",
                "2026-07-01T10:00:00Z"
        );
        OccurrenceIdentitySnapshot second = snapshot(
                tenantA,
                "imm-b",
                key("master-1", "ical-1", "2026-07-08T10:00:00Z"),
                joinUrl,
                "Occ B",
                "2026-07-08T10:00:00Z"
        );

        SeriesResolutionResult forFirst = resolver.resolve(
                tenantA,
                ImmutableEventIdentity.of("imm-a"),
                first.continuityKey(),
                joinUrl,
                List.of(first, second)
        );
        SeriesResolutionResult forSecond = resolver.resolve(
                tenantA,
                ImmutableEventIdentity.of("imm-b"),
                second.continuityKey(),
                joinUrl,
                List.of(first, second)
        );

        assertEquals(first.occurrenceId(), forFirst.matchedOccurrenceId().orElseThrow());
        assertEquals(second.occurrenceId(), forSecond.matchedOccurrenceId().orElseThrow());
        assertTrue(forFirst.sameSeriesOccurrenceIds().containsAll(List.of(first.occurrenceId(), second.occurrenceId())));
    }

    @Test
    void newOccurrenceInKnownSeriesIsDetectedWithoutJoinUrl() {
        OccurrenceIdentitySnapshot existing = snapshot(
                tenantA,
                "imm-old",
                key("master-9", "ical-9", "2026-07-01T10:00:00Z"),
                joinUrl,
                "Week 1",
                "2026-07-01T10:00:00Z"
        );
        OccurrenceContinuityKey newKey = key("master-9", "ical-9", "2026-07-15T10:00:00Z");

        SeriesResolutionResult result = resolver.resolve(
                tenantA,
                ImmutableEventIdentity.of("imm-new"),
                newKey,
                null,
                List.of(existing)
        );

        assertEquals(SeriesResolutionResult.ResolutionStatus.MATCHED_SERIES_NEW_OCCURRENCE, result.status());
        assertTrue(result.sameSeriesOccurrenceIds().contains(existing.occurrenceId()));
    }

    @Test
    void tenantIsolationIgnoresOtherTenantOccurrences() {
        OccurrenceContinuityKey key = key("master-x", "ical-x", "2026-07-01T10:00:00Z");
        OccurrenceIdentitySnapshot otherTenant = snapshot(
                tenantB,
                "imm-shared-looking",
                key,
                joinUrl,
                "Secret",
                "2026-07-01T10:00:00Z"
        );

        SeriesResolutionResult result = resolver.resolve(
                tenantA,
                ImmutableEventIdentity.of("imm-shared-looking"),
                key,
                joinUrl,
                List.of(otherTenant)
        );

        assertEquals(SeriesResolutionResult.ResolutionStatus.NO_MATCH, result.status());
        assertTrue(result.matchedOccurrenceId().isEmpty());
        assertTrue(result.sameSeriesOccurrenceIds().isEmpty());
    }

    private static OccurrenceContinuityKey key(String master, String ical, String originalStart) {
        return new OccurrenceContinuityKey(master, ical, Instant.parse(originalStart));
    }

    private static OccurrenceIdentitySnapshot snapshot(
            UUID tenantId,
            String immutableId,
            OccurrenceContinuityKey key,
            String joinWebUrl,
            String title,
            String scheduledStart
    ) {
        return new OccurrenceIdentitySnapshot(
                UUID.randomUUID(),
                tenantId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                ImmutableEventIdentity.of(immutableId),
                key,
                joinWebUrl,
                title,
                Instant.parse(scheduledStart),
                Instant.parse(scheduledStart).plusSeconds(3600)
        );
    }
}
