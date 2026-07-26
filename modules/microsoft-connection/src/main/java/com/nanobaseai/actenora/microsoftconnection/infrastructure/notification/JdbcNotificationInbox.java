package com.nanobaseai.actenora.microsoftconnection.infrastructure.notification;

import com.nanobaseai.actenora.microsoftconnection.application.port.NotificationInbox;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Objects;

/**
 * Durable Graph notification idempotency gate (V132 notification_inbox).
 * Uses insert-if-absent; returns true when this process claimed first sight.
 */
public final class JdbcNotificationInbox implements NotificationInbox {

    private final JdbcTemplate jdbc;

    public JdbcNotificationInbox(JdbcTemplate jdbcTemplate) {
        this.jdbc = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public boolean claim(String consumerName, String notificationId) {
        if (consumerName == null || consumerName.isBlank()) {
            throw new IllegalArgumentException("consumerName must not be blank");
        }
        if (notificationId == null || notificationId.isBlank()) {
            throw new IllegalArgumentException("notificationId must not be blank");
        }
        int inserted = jdbc.update(
                """
                        INSERT INTO microsoftconnection.notification_inbox (consumer_name, notification_id)
                        VALUES (?, ?)
                        ON CONFLICT (consumer_name, notification_id) DO NOTHING
                        """,
                consumerName.trim(),
                notificationId.trim()
        );
        return inserted == 1;
    }
}
