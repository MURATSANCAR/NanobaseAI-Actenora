package com.nanobaseai.actenora.meeting.api.dto;

import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrenceStatus;
import com.nanobaseai.actenora.meeting.domain.model.ProcessingPriority;

import java.time.Instant;
import java.util.UUID;

public record MeetingResponse(
        UUID id,
        UUID tenantId,
        UUID meetingSeriesId,
        UUID businessContextId,
        String graphEventImmutableId,
        String icalUid,
        Instant originalStartAt,
        String teamsMeetingId,
        String chatId,
        String joinWebUrl,
        String title,
        UUID organizerUserId,
        Instant scheduledStartAt,
        Instant scheduledEndAt,
        Instant actualStartAt,
        Instant actualEndAt,
        MeetingOccurrenceStatus status,
        ProcessingPriority processingPriority,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
