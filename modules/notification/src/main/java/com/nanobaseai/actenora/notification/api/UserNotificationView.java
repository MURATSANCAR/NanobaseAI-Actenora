package com.nanobaseai.actenora.notification.api;

import com.nanobaseai.actenora.notification.domain.UserNotificationType;

import java.time.Instant;
import java.util.UUID;

public record UserNotificationView(
        UUID id,
        UserNotificationType type,
        String title,
        String body,
        String href,
        Instant createdAt,
        Instant readAt
) {
}
