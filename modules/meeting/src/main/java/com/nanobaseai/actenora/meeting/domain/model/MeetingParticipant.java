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
    private final String entraUserId;
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
