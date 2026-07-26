package com.nanobaseai.actenora.microsoftconnection.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Meeting / calendar participant metadata from Graph.
 */
public record ParticipantMetadata(
        String id,
        String displayName,
        String email,
        String role,
        String upn,
        Instant joinedAt,
        Instant leftAt,
        Integer totalAttendanceInSeconds
) {

    public ParticipantMetadata {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("participant id must not be blank");
        }
    }

    public ParticipantMetadata(String id, String displayName, String email, String role, String upn) {
        this(id, displayName, email, role, upn, null, null, null);
    }

    public Optional<String> emailOptional() {
        return Optional.ofNullable(email).filter(s -> !s.isBlank());
    }

    public Optional<String> upnOptional() {
        return Optional.ofNullable(upn).filter(s -> !s.isBlank());
    }

    public boolean attended() {
        if (totalAttendanceInSeconds != null && totalAttendanceInSeconds > 0) {
            return true;
        }
        return joinedAt != null;
    }
}
