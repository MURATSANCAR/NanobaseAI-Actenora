package com.nanobaseai.actenora.meeting.domain.exception;

import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrenceStatus;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

public final class InvalidMeetingTransitionException extends ActenoraException {

    public InvalidMeetingTransitionException(MeetingOccurrenceStatus from, MeetingOccurrenceStatus to) {
        super(
                "INVALID_MEETING_TRANSITION",
                "Cannot transition meeting from " + from + " to " + to
        );
    }
}
