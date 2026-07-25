package com.nanobaseai.actenora.meeting.domain.exception;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

import java.util.UUID;

public final class MeetingNotFoundException extends ActenoraException {

    public MeetingNotFoundException(UUID id) {
        super("MEETING_NOT_FOUND", "Meeting not found: " + id);
    }
}
