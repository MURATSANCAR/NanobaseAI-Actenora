package com.nanobaseai.actenora.meeting.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Graph attendance-report sync payload: who actually joined the online meeting.
 */
public record ApplyAttendanceRequest(
        List<AttendanceRecord> attended,
        boolean markMissingAsAbsent
) {
    public record AttendanceRecord(
            String email,
            String entraUserId,
            String displayName,
            /** Graph attendance role when present: Organizer, Presenter, Attendee. */
            String role,
            Instant joinedAt,
            Instant leftAt
    ) {
    }
}
