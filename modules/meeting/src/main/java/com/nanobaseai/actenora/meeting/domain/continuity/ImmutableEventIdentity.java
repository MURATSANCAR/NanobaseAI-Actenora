package com.nanobaseai.actenora.meeting.domain.continuity;

import java.util.Objects;

/**
 * Stable Microsoft Graph event identity for an occurrence.
 * Join URLs are intentionally excluded — they are not identity.
 */
public record ImmutableEventIdentity(String graphEventImmutableId) {

    public ImmutableEventIdentity {
        Objects.requireNonNull(graphEventImmutableId, "graphEventImmutableId");
        if (graphEventImmutableId.isBlank()) {
            throw new IllegalArgumentException("graphEventImmutableId must not be blank");
        }
    }

    public static ImmutableEventIdentity of(String graphEventImmutableId) {
        return new ImmutableEventIdentity(graphEventImmutableId);
    }
}
