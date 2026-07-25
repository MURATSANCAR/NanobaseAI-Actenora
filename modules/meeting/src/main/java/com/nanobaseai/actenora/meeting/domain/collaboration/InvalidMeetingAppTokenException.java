package com.nanobaseai.actenora.meeting.domain.collaboration;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

public final class InvalidMeetingAppTokenException extends ActenoraException {

    public InvalidMeetingAppTokenException(String reason) {
        super("INVALID_MEETING_APP_TOKEN", "Teams context alone is not trusted: " + reason);
    }
}
