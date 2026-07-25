package com.nanobaseai.actenora.meeting.domain.collaboration;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

import java.util.UUID;

public final class UnauthorizedMeetingAccessException extends ActenoraException {

    public UnauthorizedMeetingAccessException(UUID meetingId) {
        super("UNAUTHORIZED_MEETING_ACCESS", "Caller is not a member of meeting: " + meetingId);
    }
}
