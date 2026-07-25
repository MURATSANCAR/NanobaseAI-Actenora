package com.nanobaseai.actenora.microsoftconnection.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Delta query cursor for a mailbox calendar.
 */
public record CalendarSyncCursor(
        UUID tenantId,
        String userId,
        String deltaLink,
        String nextLink,
        Instant updatedAt
) {

    public CalendarSyncCursor {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
    }

    public static CalendarSyncCursor initial(UUID tenantId, String userId, Instant now) {
        return new CalendarSyncCursor(tenantId, userId, null, null, now);
    }

    public Optional<String> deltaLinkOptional() {
        return Optional.ofNullable(deltaLink).filter(s -> !s.isBlank());
    }

    public Optional<String> nextLinkOptional() {
        return Optional.ofNullable(nextLink).filter(s -> !s.isBlank());
    }

    public CalendarSyncCursor withPage(String nextLink, String deltaLink, Instant now) {
        return new CalendarSyncCursor(tenantId, userId, deltaLink, nextLink, now);
    }
}
