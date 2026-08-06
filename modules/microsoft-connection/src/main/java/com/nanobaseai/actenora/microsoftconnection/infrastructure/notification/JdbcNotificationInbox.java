package com.nanobaseai.actenora.microsoftconnection.infrastructure.notification;

import com.nanobaseai.actenora.microsoftconnection.application.port.NotificationInbox;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.Objects;

/**
 * Durable Graph notification idempotency gate (V132 notification_inbox).
 * Uses insert-if-absent; returns true when this process claimed first sight.
 *
 * <p>Implemented with a savepoint + plain INSERT so duplicate claims do not abort
 * the surrounding Spring transaction (PostgreSQL) and still work on H2 unit tests.
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
        String consumer = consumerName.trim();
        String notification = notificationId.trim();
        Boolean claimed = jdbc.execute((ConnectionCallback<Boolean>) connection -> {
            boolean restoreAutoCommit = false;
            if (connection.getAutoCommit()) {
                connection.setAutoCommit(false);
                restoreAutoCommit = true;
            }
            try {
                Savepoint savepoint = connection.setSavepoint("graph_notification_claim");
                try (PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO microsoftconnection.notification_inbox (consumer_name, notification_id)
                        VALUES (?, ?)
                        """)) {
                    ps.setString(1, consumer);
                    ps.setString(2, notification);
                    ps.executeUpdate();
                    connection.releaseSavepoint(savepoint);
                    if (restoreAutoCommit) {
                        connection.commit();
                    }
                    return true;
                } catch (SQLException ex) {
                    connection.rollback(savepoint);
                    if (restoreAutoCommit) {
                        connection.commit();
                    }
                    if (isUniqueViolation(ex)) {
                        return false;
                    }
                    throw ex;
                }
            } finally {
                if (restoreAutoCommit) {
                    connection.setAutoCommit(true);
                }
            }
        });
        return Boolean.TRUE.equals(claimed);
    }

    private static boolean isUniqueViolation(SQLException ex) {
        for (SQLException current = ex; current != null; current = current.getNextException()) {
            String sqlState = current.getSQLState();
            if ("23505".equals(sqlState)) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("unique") || lower.contains("duplicate")) {
                    return true;
                }
            }
        }
        return false;
    }
}
