package com.nanobaseai.actenora.delivery.application.model;

/**
 * One participant row for branded note PDF / email rendering.
 */
public record MeetingNoteParticipant(
        String name,
        String email,
        String role,
        String attendance
) {
    public MeetingNoteParticipant {
        name = name == null ? "" : name.trim();
        email = email == null ? "" : email.trim();
        role = role == null ? "" : role.trim();
        attendance = attendance == null ? "" : attendance.trim();
    }

    public static MeetingNoteParticipant ofName(String name) {
        return new MeetingNoteParticipant(name, "", "Katılımcı", "");
    }
}
