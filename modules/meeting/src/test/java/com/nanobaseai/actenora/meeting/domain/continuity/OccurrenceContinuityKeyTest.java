package com.nanobaseai.actenora.meeting.domain.continuity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OccurrenceContinuityKeyTest {

    @Test
    void renamedMeetingKeepsIdentity() {
        UUID id = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        OccurrenceContinuityKey key = new OccurrenceContinuityKey(
                "series-master-1",
                "ical-uid-1",
                Instant.parse("2026-07-01T10:00:00Z")
        );
        OccurrenceIdentitySnapshot original = new OccurrenceIdentitySnapshot(
                id,
                tenant,
                UUID.randomUUID(),
                UUID.randomUUID(),
                ImmutableEventIdentity.of("event-immutable-1"),
                key,
                "https://teams.microsoft.com/l/meetup-join/shared",
                "Weekly Sync",
                Instant.parse("2026-07-01T10:00:00Z"),
                Instant.parse("2026-07-01T11:00:00Z")
        );

        OccurrenceIdentitySnapshot renamed = original.withTitle("Weekly Sync (Renamed)");

        assertEquals(original.immutableEventIdentity(), renamed.immutableEventIdentity());
        assertEquals(original.continuityKey(), renamed.continuityKey());
        assertNotEquals(original.title(), renamed.title());
        assertTrue(renamed.continuityKey().matches(key));
    }
}
