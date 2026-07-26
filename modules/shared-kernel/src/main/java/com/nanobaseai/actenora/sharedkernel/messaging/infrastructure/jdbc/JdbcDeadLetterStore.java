package com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.jdbc;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.DeadLetterEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.port.DeadLetterStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC-backed dead-letter store ({@code <schema>.dead_letter_event}).
 */
public final class JdbcDeadLetterStore implements DeadLetterStore {

    private final DataSource dataSource;
    private final String deadLetterTable;

    public JdbcDeadLetterStore(DataSource dataSource, String schema) {
        this.dataSource = dataSource;
        this.deadLetterTable = JdbcMessagingSchema.table(schema, "dead_letter_event");
    }

    @Override
    public void append(DeadLetterEvent event) {
        String sql = """
                INSERT INTO %s (
                    id, source, event_id, consumer_name, event_type, event_version, payload_json,
                    failure_code, failure_detail, correlation_id, tenant_id, attempts,
                    dead_lettered_at, replayed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(deadLetterTable);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindDeadLetter(ps, event);
            if (ps.executeUpdate() != 1) {
                throw new IllegalStateException("DLQ append failed for " + event.id());
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("DLQ append failed for " + event.id(), ex);
        }
    }

    @Override
    public Optional<DeadLetterEvent> findById(UUID id) {
        String sql = "SELECT * FROM " + deadLetterTable + " WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapDeadLetter(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("DLQ findById failed for " + id, ex);
        }
    }

    @Override
    public Optional<DeadLetterEvent> findByEventId(UUID eventId) {
        String sql = """
                SELECT * FROM %s WHERE event_id = ?
                ORDER BY dead_lettered_at DESC LIMIT 1
                """.formatted(deadLetterTable);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapDeadLetter(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("DLQ findByEventId failed for " + eventId, ex);
        }
    }

    @Override
    public List<DeadLetterEvent> listOpen(int limit) {
        String sql = """
                SELECT * FROM %s WHERE replayed_at IS NULL
                ORDER BY dead_lettered_at ASC LIMIT ?
                """.formatted(deadLetterTable);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<DeadLetterEvent> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapDeadLetter(rs));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("DLQ listOpen failed", ex);
        }
    }

    @Override
    public void save(DeadLetterEvent event) {
        String sql = """
                UPDATE %s SET
                    source = ?,
                    consumer_name = ?,
                    event_type = ?,
                    event_version = ?,
                    payload_json = ?,
                    failure_code = ?,
                    failure_detail = ?,
                    correlation_id = ?,
                    tenant_id = ?,
                    attempts = ?,
                    dead_lettered_at = ?,
                    replayed_at = ?
                WHERE id = ?
                """.formatted(deadLetterTable);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, event.source().name());
            ps.setString(2, event.consumerName());
            ps.setString(3, event.eventType());
            ps.setInt(4, event.eventVersion());
            ps.setString(5, event.payloadJson());
            ps.setString(6, event.failureCode());
            ps.setString(7, event.failureDetail());
            ps.setObject(8, event.correlationId());
            ps.setObject(9, event.tenantIdOptional().map(TenantId::value).orElse(null));
            ps.setInt(10, event.attempts());
            ps.setTimestamp(11, Timestamp.from(event.deadLetteredAt()));
            ps.setTimestamp(12, event.replayedAtOptional().map(Timestamp::from).orElse(null));
            ps.setObject(13, event.id());
            if (ps.executeUpdate() != 1) {
                throw new IllegalStateException("DLQ save failed for " + event.id());
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("DLQ save failed for " + event.id(), ex);
        }
    }

    private static void bindDeadLetter(PreparedStatement ps, DeadLetterEvent event) throws SQLException {
        ps.setObject(1, event.id());
        ps.setString(2, event.source().name());
        ps.setObject(3, event.eventId());
        ps.setString(4, event.consumerName());
        ps.setString(5, event.eventType());
        ps.setInt(6, event.eventVersion());
        ps.setString(7, event.payloadJson());
        ps.setString(8, event.failureCode());
        ps.setString(9, event.failureDetail());
        ps.setObject(10, event.correlationId());
        ps.setObject(11, event.tenantIdOptional().map(TenantId::value).orElse(null));
        ps.setInt(12, event.attempts());
        ps.setTimestamp(13, Timestamp.from(event.deadLetteredAt()));
        ps.setTimestamp(14, event.replayedAtOptional().map(Timestamp::from).orElse(null));
    }

    static DeadLetterEvent mapDeadLetter(ResultSet rs) throws SQLException {
        UUID tenantId = rs.getObject("tenant_id", UUID.class);
        Timestamp replayedAt = rs.getTimestamp("replayed_at");
        return new DeadLetterEvent(
                rs.getObject("id", UUID.class),
                DeadLetterEvent.DeadLetterSource.valueOf(rs.getString("source")),
                rs.getObject("event_id", UUID.class),
                rs.getString("consumer_name"),
                rs.getString("event_type"),
                rs.getInt("event_version"),
                rs.getString("payload_json"),
                rs.getString("failure_code"),
                rs.getString("failure_detail"),
                rs.getObject("correlation_id", UUID.class),
                tenantId == null ? null : TenantId.of(tenantId),
                rs.getInt("attempts"),
                rs.getTimestamp("dead_lettered_at").toInstant(),
                replayedAt == null ? null : replayedAt.toInstant()
        );
    }
}
