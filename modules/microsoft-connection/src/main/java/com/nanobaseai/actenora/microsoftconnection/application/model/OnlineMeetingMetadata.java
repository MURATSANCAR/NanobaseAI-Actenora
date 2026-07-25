package com.nanobaseai.actenora.microsoftconnection.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Online meeting metadata from Graph.
 */
public record OnlineMeetingMetadata(
        String meetingId,
        String joinWebUrl,
        String subject,
        Instant startDateTime,
        Instant endDateTime,
        String chatId,
        boolean isBroadcast
) {

    public OnlineMeetingMetadata {
        Objects.requireNonNull(meetingId, "meetingId");
        if (meetingId.isBlank()) {
            throw new IllegalArgumentException("meetingId must not be blank");
        }
    }

    public Optional<String> joinWebUrlOptional() {
        return Optional.ofNullable(joinWebUrl).filter(s -> !s.isBlank());
    }

    public Optional<String> chatIdOptional() {
        return Optional.ofNullable(chatId).filter(s -> !s.isBlank());
    }
}
