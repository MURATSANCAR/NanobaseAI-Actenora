package com.nanobaseai.actenora.microsoftconnection.domain.identity;

import java.util.Objects;

/**
 * Stable Microsoft Graph event identity for an occurrence.
 * Join URLs are intentionally excluded — they are not identity.
 */
public record ImmutableGraphEventIdentity(String graphEventImmutableId) {

    public ImmutableGraphEventIdentity {
        Objects.requireNonNull(graphEventImmutableId, "graphEventImmutableId");
        if (graphEventImmutableId.isBlank()) {
            throw new IllegalArgumentException("graphEventImmutableId must not be blank");
        }
    }

    public static ImmutableGraphEventIdentity of(String graphEventImmutableId) {
        return new ImmutableGraphEventIdentity(graphEventImmutableId);
    }
}
