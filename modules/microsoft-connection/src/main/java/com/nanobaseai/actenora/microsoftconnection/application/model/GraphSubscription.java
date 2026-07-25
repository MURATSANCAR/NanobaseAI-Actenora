package com.nanobaseai.actenora.microsoftconnection.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Graph change-notification subscription owned by Actenora.
 */
public record GraphSubscription(
        UUID tenantId,
        String subscriptionId,
        String resource,
        String changeType,
        String notificationUrl,
        String clientState,
        Instant expirationDateTime,
        String applicationId
) {

    public GraphSubscription {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(subscriptionId, "subscriptionId");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(expirationDateTime, "expirationDateTime");
        if (subscriptionId.isBlank()) {
            throw new IllegalArgumentException("subscriptionId must not be blank");
        }
    }

    public GraphSubscription withExpiration(Instant newExpiration) {
        return new GraphSubscription(
                tenantId,
                subscriptionId,
                resource,
                changeType,
                notificationUrl,
                clientState,
                newExpiration,
                applicationId
        );
    }

    public boolean expiresBefore(Instant threshold) {
        return expirationDateTime.isBefore(threshold);
    }
}
