package com.nanobaseai.actenora.meeting.domain.collaboration;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

class MarkerOffsetAndPrivatePolicyTest {

    @Test
    void offsetUsesServerClockOnly() {
        Instant anchor = Instant.parse("2026-07-25T09:00:00Z");
        Instant serverNow = Instant.parse("2026-07-25T09:01:15Z");
        assertEquals(75_000L, MarkerOffsetCalculator.offsetMs(anchor, serverNow));
    }

    @Test
    void organizerDoesNotGetPrivateAccessByDefault() {
        PrivateNote note = PrivateNote.create(
                com.nanobaseai.actenora.sharedkernel.domain.TenantId.random(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "mine",
                Instant.parse("2026-07-25T10:00:00Z")
        );
        UUID organizer = UUID.randomUUID();
        assertFalse(PrivateNoteAccessPolicy.canHumanRead(note, organizer, true, true));
        assertTrue(PrivateNoteAccessPolicy.canHumanRead(note, note.ownerUserId(), false, false));
        assertFalse(PrivateNoteAccessPolicy.canAiUse(note));
    }
}
