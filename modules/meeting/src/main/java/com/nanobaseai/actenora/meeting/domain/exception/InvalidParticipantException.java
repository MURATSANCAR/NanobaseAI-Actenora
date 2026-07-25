package com.nanobaseai.actenora.meeting.domain.exception;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

public final class InvalidParticipantException extends ActenoraException {

    public InvalidParticipantException(String message) {
        super("INVALID_PARTICIPANT", message);
    }
}
