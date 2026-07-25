package com.nanobaseai.actenora.meeting.domain.collaboration;

import java.util.Objects;
import java.util.UUID;

/**
 * Private notes are owner-only. Organizer and tenant admin roles do not grant access by default.
 * AI consumers require an explicit owner-granted consent flag.
 */
public final class PrivateNoteAccessPolicy {

    private PrivateNoteAccessPolicy() {
    }

    public static boolean canHumanRead(PrivateNote note, UUID actorUserId, boolean actorIsOrganizer, boolean actorIsAdmin) {
        Objects.requireNonNull(note, "note");
        Objects.requireNonNull(actorUserId, "actorUserId");
        if (note.ownerUserId().equals(actorUserId)) {
            return true;
        }
        // Organizer / admin must not see private notes by default.
        return false;
    }

    public static boolean canAiUse(PrivateNote note) {
        Objects.requireNonNull(note, "note");
        return note.aiUseAllowed();
    }
}
