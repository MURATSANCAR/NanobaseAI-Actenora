package com.nanobaseai.actenora.microsoftconnection.application.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Graph subscription lifecycle notification (reauthorizationRequired, missed, removed, …).
 */
public record LifecycleNotification(
        String notificationId,
        String subscriptionId,
        String lifecycleEvent,
        String clientState,
        String tenantId
) {

    public LifecycleNotification {
        Objects.requireNonNull(notificationId, "notificationId");
        Objects.requireNonNull(subscriptionId, "subscriptionId");
        Objects.requireNonNull(lifecycleEvent, "lifecycleEvent");
        if (notificationId.isBlank()) {
            throw new IllegalArgumentException("notificationId must not be blank");
        }
    }

    public boolean requiresReauthorization() {
        return "reauthorizationRequired".equalsIgnoreCase(lifecycleEvent);
    }

    public boolean subscriptionRemoved() {
        return "subscriptionRemoved".equalsIgnoreCase(lifecycleEvent);
    }

    public boolean missed() {
        return "missed".equalsIgnoreCase(lifecycleEvent);
    }

    public Optional<String> clientStateOptional() {
        return Optional.ofNullable(clientState);
    }
}
