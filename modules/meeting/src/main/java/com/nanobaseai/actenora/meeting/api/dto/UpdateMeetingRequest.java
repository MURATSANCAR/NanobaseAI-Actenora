package com.nanobaseai.actenora.meeting.api.dto;

import com.nanobaseai.actenora.meeting.domain.model.ProcessingPriority;

import java.time.Instant;

public record UpdateMeetingRequest(
        String title,
        Instant scheduledStartAt,
        Instant scheduledEndAt,
        String graphEventImmutableId,
        String icalUid,
        Instant originalStartAt,
        String teamsMeetingId,
        String chatId,
        String joinWebUrl,
        ProcessingPriority processingPriority,
        long expectedVersion
) {
}
