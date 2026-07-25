package com.nanobaseai.actenora.microsoftconnection.application.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Request to create a Graph subscription.
 */
public record SubscriptionCreateRequest(
        String resource,
        String changeType,
        String notificationUrl,
        String lifecycleNotificationUrl,
        String clientState,
        Duration expirationWindow
) {

    public SubscriptionCreateRequest {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(changeType, "changeType");
        Objects.requireNonNull(notificationUrl, "notificationUrl");
        Objects.requireNonNull(expirationWindow, "expirationWindow");
        if (resource.isBlank()) {
            throw new IllegalArgumentException("resource must not be blank");
        }
        if (notificationUrl.isBlank()) {
            throw new IllegalArgumentException("notificationUrl must not be blank");
        }
    }
}
