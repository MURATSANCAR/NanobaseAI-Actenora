package com.nanobaseai.actenora.microsoftconnection.application.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Graph change notification payload item.
 */
public record GraphChangeNotification(
        String notificationId,
        String subscriptionId,
        String changeType,
        String resource,
        String resourceDataId,
        String clientState,
        String tenantId
) {

    public GraphChangeNotification {
        Objects.requireNonNull(notificationId, "notificationId");
        Objects.requireNonNull(subscriptionId, "subscriptionId");
        if (notificationId.isBlank()) {
            throw new IllegalArgumentException("notificationId must not be blank");
        }
    }

    public Optional<String> resourceDataIdOptional() {
        return Optional.ofNullable(resourceDataId).filter(s -> !s.isBlank());
    }
}
