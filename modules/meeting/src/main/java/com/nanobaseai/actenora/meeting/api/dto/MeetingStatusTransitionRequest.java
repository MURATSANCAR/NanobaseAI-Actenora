package com.nanobaseai.actenora.meeting.api.dto;

import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrenceStatus;

public record MeetingStatusTransitionRequest(
        MeetingOccurrenceStatus targetStatus,
        long expectedVersion
) {
}
