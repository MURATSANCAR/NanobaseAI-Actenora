package com.nanobaseai.actenora.notification.infrastructure.persistence;

import com.nanobaseai.actenora.notification.application.port.UserNotificationRepository;
import com.nanobaseai.actenora.notification.domain.UserNotification;
import com.nanobaseai.actenora.notification.domain.UserNotificationType;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** JDBC store for {@code notification.user_notifications}. */
public final class JdbcUserNotificationRepository implements UserNotificationRepository {

    private static final RowMapper<UserNotification> ROW_MAPPER = (rs, rowNum) -> UserNotification.rehydrate(
            rs.getObject("id", UUID.class),
            TenantId.of(rs.getObject("tenant_id", UUID.class)),
            rs.getString("recipient_oid"),
            UserNotificationType.valueOf(rs.getString("type")),
            rs.getString("title"),
            rs.getString("body"),
            rs.getString("href"),
            rs.getString("dedupe_key"),
            JdbcInstant.get(rs, "created_at"),
            JdbcInstant.get(rs, "read_at")
    );

    private final JdbcTemplate jdbc;

    public JdbcUserNotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public Optional<UserNotification> insertIfAbsent(UserNotification notification) {
        String sql = """
                INSERT INTO notification.user_notifications (
                    id, tenant_id, recipient_oid, type, title, body, href, dedupe_key, created_at, read_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, recipient_oid, type, dedupe_key) DO NOTHING
                """;
        try {
            int updated = jdbc.update(sql,
                    notification.id(),
                    notification.tenantId().value(),
                    notification.recipientOid(),
                    notification.type().name(),
                    notification.title(),
                    notification.body(),
                    notification.href(),
                    notification.dedupeKey(),
                    JdbcInstant.toTimestamp(notification.createdAt()),
                    notification.readAt().map(JdbcInstant::toTimestamp).orElse(null)
            );
            return updated > 0 ? Optional.of(notification) : Optional.empty();
        } catch (DuplicateKeyException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<UserNotification> findById(TenantId tenantId, UUID id) {
        String sql = """
                SELECT id, tenant_id, recipient_oid, type, title, body, href, dedupe_key, created_at, read_at
                FROM notification.user_notifications
                WHERE id = ? AND tenant_id = ?
                """;
        return jdbc.query(sql, ROW_MAPPER, id, tenantId.value()).stream().findFirst();
    }

    @Override
    public List<UserNotification> listForRecipient(TenantId tenantId, String recipientOid, int limit) {
        int capped = Math.max(1, Math.min(limit, 100));
        String sql = """
                SELECT id, tenant_id, recipient_oid, type, title, body, href, dedupe_key, created_at, read_at
                FROM notification.user_notifications
                WHERE tenant_id = ? AND lower(recipient_oid) = lower(?)
                ORDER BY created_at DESC
                LIMIT ?
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId.value(), recipientOid, capped);
    }

    @Override
    public int countUnread(TenantId tenantId, String recipientOid) {
        String sql = """
                SELECT COUNT(*) FROM notification.user_notifications
                WHERE tenant_id = ? AND lower(recipient_oid) = lower(?) AND read_at IS NULL
                """;
        Integer count = jdbc.queryForObject(sql, Integer.class, tenantId.value(), recipientOid);
        return count == null ? 0 : count;
    }

    @Override
    public boolean markRead(TenantId tenantId, String recipientOid, UUID id) {
        String sql = """
                UPDATE notification.user_notifications
                SET read_at = COALESCE(read_at, ?)
                WHERE id = ? AND tenant_id = ? AND lower(recipient_oid) = lower(?)
                """;
        return jdbc.update(sql, JdbcInstant.toTimestamp(Instant.now()), id, tenantId.value(), recipientOid) > 0;
    }

    @Override
    public int markAllRead(TenantId tenantId, String recipientOid) {
        String sql = """
                UPDATE notification.user_notifications
                SET read_at = ?
                WHERE tenant_id = ? AND lower(recipient_oid) = lower(?) AND read_at IS NULL
                """;
        return jdbc.update(sql, JdbcInstant.toTimestamp(Instant.now()), tenantId.value(), recipientOid);
    }

    @Override
    public boolean exists(TenantId tenantId, String recipientOid, UserNotificationType type, String dedupeKey) {
        String sql = """
                SELECT COUNT(*) FROM notification.user_notifications
                WHERE tenant_id = ? AND lower(recipient_oid) = lower(?) AND type = ? AND dedupe_key = ?
                """;
        Integer count = jdbc.queryForObject(
                sql, Integer.class, tenantId.value(), recipientOid, type.name(), dedupeKey
        );
        return count != null && count > 0;
    }
}
