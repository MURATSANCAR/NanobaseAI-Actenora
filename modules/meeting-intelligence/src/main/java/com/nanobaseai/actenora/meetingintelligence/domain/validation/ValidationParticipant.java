package com.nanobaseai.actenora.meetingintelligence.domain.validation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Meeting participant snapshot for owner validation.
 */
public record ValidationParticipant(
        UUID participantId,
        String displayName,
        String email,
        String entraUserId
) {
    public ValidationParticipant {
        Objects.requireNonNull(participantId, "participantId");
        Objects.requireNonNull(displayName, "displayName");
    }

    public Optional<String> emailOptional() {
        return Optional.ofNullable(email);
    }

    public Optional<String> entraUserIdOptional() {
        return Optional.ofNullable(entraUserId);
    }
}
