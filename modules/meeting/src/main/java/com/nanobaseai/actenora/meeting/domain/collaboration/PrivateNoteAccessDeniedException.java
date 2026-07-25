package com.nanobaseai.actenora.meeting.domain.collaboration;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

import java.util.UUID;

public final class PrivateNoteAccessDeniedException extends ActenoraException {

    public PrivateNoteAccessDeniedException(UUID noteId) {
        super("PRIVATE_NOTE_ACCESS_DENIED", "Private note is owner-only: " + noteId);
    }
}
