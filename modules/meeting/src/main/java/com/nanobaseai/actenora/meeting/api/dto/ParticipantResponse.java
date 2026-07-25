package com.nanobaseai.actenora.meeting.api.dto;

import com.nanobaseai.actenora.meeting.domain.model.AttendanceStatus;
import com.nanobaseai.actenora.meeting.domain.model.ParticipantType;

import java.time.Instant;
import java.util.UUID;

public record ParticipantResponse(
        UUID id,
        UUID meetingOccurrenceId,
        String entraUserId,
        String displayName,
        String email,
        ParticipantType participantType,
        AttendanceStatus attendanceStatus,
        Instant joinedAt,
        Instant leftAt,
        boolean external
) {
}
