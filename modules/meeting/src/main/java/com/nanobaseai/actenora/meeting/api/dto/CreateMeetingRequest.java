package com.nanobaseai.actenora.meeting.api.dto;

import com.nanobaseai.actenora.meeting.domain.model.MeetingType;
import com.nanobaseai.actenora.meeting.domain.model.ProcessingPriority;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateMeetingRequest(
        UUID businessContextId,
        UUID meetingSeriesId,
        String graphSeriesMasterId,
        String graphEventImmutableId,
        String icalUid,
        Instant originalStartAt,
        String teamsMeetingId,
        String chatId,
        String joinWebUrl,
        String title,
        MeetingType meetingType,
        Instant scheduledStartAt,
        Instant scheduledEndAt,
        ProcessingPriority processingPriority,
        List<ParticipantInput> participants
) {
    public record ParticipantInput(
            String entraUserId,
            String displayName,
            String email,
            String participantType,
            boolean external
    ) {
    }
}
