package com.nanobaseai.actenora.meetingintelligence.domain.exception;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

import java.util.UUID;

public final class MeetingNoteNotFoundException extends ActenoraException {

    public MeetingNoteNotFoundException(UUID noteId) {
        super("MEETING_NOTE_NOT_FOUND", "Meeting note not found: " + noteId);
    }
}
