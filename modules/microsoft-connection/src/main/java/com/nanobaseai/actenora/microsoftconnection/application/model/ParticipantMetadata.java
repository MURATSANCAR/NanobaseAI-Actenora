package com.nanobaseai.actenora.microsoftconnection.application.model;

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
        String upn
) {

    public ParticipantMetadata {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("participant id must not be blank");
        }
    }

    public Optional<String> emailOptional() {
        return Optional.ofNullable(email).filter(s -> !s.isBlank());
    }

    public Optional<String> upnOptional() {
        return Optional.ofNullable(upn).filter(s -> !s.isBlank());
    }
}
