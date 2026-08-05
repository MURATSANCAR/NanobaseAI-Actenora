package com.nanobaseai.actenora.meeting.domain.model;

import com.nanobaseai.actenora.meeting.domain.exception.InvalidParticipantException;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class MeetingParticipant {

    private final UUID id;
    private final TenantId tenantId;
    private final UUID meetingOccurrenceId;
    private String entraUserId;
    private String displayName;
    private String email;
    private ParticipantType participantType;
    private AttendanceStatus attendanceStatus;
    private Instant joinedAt;
    private Instant leftAt;
    private final boolean external;

    private MeetingParticipant(
            UUID id,
            TenantId tenantId,
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
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.meetingOccurrenceId = Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        this.entraUserId = entraUserId;
        this.displayName = requireText(displayName, "displayName");
        this.email = requireEmail(email);
        this.participantType = Objects.requireNonNull(participantType, "participantType");
        this.attendanceStatus = Objects.requireNonNull(attendanceStatus, "attendanceStatus");
        this.joinedAt = joinedAt;
        this.leftAt = leftAt;
        this.external = external;
        validateExternalRules();
    }

    public static MeetingParticipant create(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            String entraUserId,
            String displayName,
            String email,
            ParticipantType participantType,
            boolean external
    ) {
        return new MeetingParticipant(
                UUID.randomUUID(),
                tenantId,
                meetingOccurrenceId,
                entraUserId,
                displayName,
                email,
                participantType == null ? ParticipantType.REQUIRED : participantType,
                AttendanceStatus.INVITED,
                null,
                null,
                external
        );
    }

    public static MeetingParticipant rehydrate(
            UUID id,
            TenantId tenantId,
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
        return new MeetingParticipant(
                id, tenantId, meetingOccurrenceId, entraUserId, displayName, email,
                participantType, attendanceStatus, joinedAt, leftAt, external
        );
    }

    private void validateExternalRules() {
        if (external) {
            if (email == null || email.isBlank()) {
                throw new InvalidParticipantException("External participants require an email");
            }
        } else if (entraUserId == null || entraUserId.isBlank()) {
            throw new InvalidParticipantException("Internal participants require an Entra user id");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String requireEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new InvalidParticipantException("Participant email is required");
        }
        String normalized = email.trim().toLowerCase();
        if (!normalized.contains("@")) {
            throw new InvalidParticipantException("Participant email is invalid");
        }
        return normalized;
    }

    public void markJoined(Instant joinedAt, Instant leftAt) {
        this.attendanceStatus = AttendanceStatus.JOINED;
        this.joinedAt = joinedAt;
        this.leftAt = leftAt;
    }

    public void markLeft(Instant leftAt) {
        this.attendanceStatus = AttendanceStatus.LEFT;
        this.leftAt = leftAt;
    }

    public void markAbsent() {
        if (attendanceStatus == AttendanceStatus.JOINED || attendanceStatus == AttendanceStatus.LEFT) {
            return;
        }
        this.attendanceStatus = AttendanceStatus.ABSENT;
        this.joinedAt = null;
        this.leftAt = null;
    }

    /** Calendar RSVP before attendance report (does not override JOINED/LEFT/ABSENT). */
    public void applyInviteResponse(AttendanceStatus response) {
        if (response == null) {
            return;
        }
        if (attendanceStatus == AttendanceStatus.JOINED
                || attendanceStatus == AttendanceStatus.LEFT
                || attendanceStatus == AttendanceStatus.ABSENT) {
            return;
        }
        if (response == AttendanceStatus.ACCEPTED
                || response == AttendanceStatus.DECLINED
                || response == AttendanceStatus.TENTATIVE
                || response == AttendanceStatus.INVITED) {
            this.attendanceStatus = response;
        }
    }

    public void promoteToOrganizer() {
        this.participantType = ParticipantType.ORGANIZER;
    }

    public void rename(String newDisplayName) {
        this.displayName = requireText(newDisplayName, "displayName");
    }

    public void assignParticipantType(ParticipantType type) {
        if (type == null) {
            return;
        }
        // Do not demote organizer once set (calendar re-sync may send required|accepted for organizer row).
        if (this.participantType == ParticipantType.ORGANIZER && type != ParticipantType.ORGANIZER) {
            return;
        }
        this.participantType = type;
    }

    /**
     * Replace a placeholder entra id (often calendar email) with a real Entra object id from Teams attendance.
     * No-op when the incoming id is blank or the existing value is already that GUID.
     */
    public void linkEntraUserId(String objectId) {
        if (objectId == null || objectId.isBlank()) {
            return;
        }
        String trimmed = objectId.trim();
        if (!looksLikeGuid(trimmed)) {
            return;
        }
        if (trimmed.equalsIgnoreCase(this.entraUserId)) {
            return;
        }
        this.entraUserId = trimmed;
    }

    private static boolean looksLikeGuid(String value) {
        return value.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public UUID meetingOccurrenceId() { return meetingOccurrenceId; }
    public String entraUserId() { return entraUserId; }
    public String displayName() { return displayName; }
    public String email() { return email; }
    public ParticipantType participantType() { return participantType; }
    public AttendanceStatus attendanceStatus() { return attendanceStatus; }
    public Instant joinedAt() { return joinedAt; }
    public Instant leftAt() { return leftAt; }
    public boolean isExternal() { return external; }
}
