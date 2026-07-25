package com.nanobaseai.actenora.meetingintelligence.domain.exception;

import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNoteStatus;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

public final class InvalidNoteTransitionException extends ActenoraException {

    public InvalidNoteTransitionException(MeetingNoteStatus from, MeetingNoteStatus to) {
        super(
                "INVALID_NOTE_TRANSITION",
                "Cannot transition meeting note from " + from + " to " + to
        );
    }
}
