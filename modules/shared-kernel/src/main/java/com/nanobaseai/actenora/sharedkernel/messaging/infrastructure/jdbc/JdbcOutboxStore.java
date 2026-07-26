package com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.jdbc;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxStatus;
import com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.support.TenantFairnessTracker;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC-backed transactional outbox ({@code <schema>.outbox_event}).
 */
public final class JdbcOutboxStore implements OutboxStore {

    private static final EnumSet<OutboxStatus> CLAIMABLE = EnumSet.of(
            OutboxStatus.PENDING,
            OutboxStatus.RETRY,
            OutboxStatus.PUBLISHING
    );

    private static final int FAIRNESS_POOL_MULTIPLIER = 20;
    private static final int FAIRNESS_POOL_CAP = 500;

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final String outboxTable;
    private final TenantFairnessTracker fairness;

    public JdbcOutboxStore(DataSource dataSource, String schema, TenantFairnessTracker fairness) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
        this.outboxTable = JdbcMessagingSchema.table(schema, "outbox_event");
        this.fairness = fairness;
    }

    @Override
    public void append(OutboxEvent event) {
        String sql = """
                INSERT INTO %s (
                    id, aggregate_type, aggregate_id, tenant_id, event_type, event_version,
                    payload_json, correlation_id, causation_id, trace_id, occurred_at,
                    published_at, status, attempt_count, next_attempt_at, failure_code
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(outboxTable);
        int inserted = jdbc.update(sql, ps -> bindOutbox(ps, event));
        if (inserted != 1) {
            throw new IllegalStateException("Failed to append outbox event: " + event.id());
        }
    }

    @Override
    public Optional<OutboxEvent> findById(UUID id) {
        String sql = "SELECT * FROM " + outboxTable + " WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapOutbox(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Outbox findById failed for " + id, ex);
        }
    }

    @Override
    public List<OutboxEvent> claimDue(Instant now, int limit) {
        if (limit < 1) {
            return List.of();
        }
        int poolSize = Math.min(Math.max(limit * FAIRNESS_POOL_MULTIPLIER, limit), FAIRNESS_POOL_CAP);
        String selectSql = """
                SELECT * FROM %s
                WHERE status IN ('PENDING', 'RETRY', 'PUBLISHING')
                  AND (status = 'PUBLISHING' OR next_attempt_at <= ?)
                ORDER BY next_attempt_at ASC
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """.formatted(outboxTable);
        String updateSql = "UPDATE " + outboxTable + " SET status = 'PUBLISHING' WHERE id = ?";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                List<OutboxEvent> candidates = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                    ps.setTimestamp(1, Timestamp.from(now));
                    ps.setInt(2, poolSize);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            OutboxEvent event = mapOutbox(rs);
                            if (CLAIMABLE.contains(event.status())) {
                                candidates.add(event);
                            }
                        }
                    }
                }

                List<OutboxEvent> selected = fairness.selectFair(candidates, limit, OutboxEvent::tenantId);
                List<OutboxEvent> claimed = new ArrayList<>(selected.size());
                try (PreparedStatement update = conn.prepareStatement(updateSql)) {
                    for (OutboxEvent event : selected) {
                        update.setObject(1, event.id());
                        if (update.executeUpdate() == 1) {
                            event.markPublishing();
                            claimed.add(event);
                        }
                    }
                }
                conn.commit();
                return List.copyOf(claimed);
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Outbox claimDue failed", ex);
        }
    }

    @Override
    public void save(OutboxEvent event) {
        String sql = """
                UPDATE %s SET
                    published_at = ?,
                    status = ?,
                    attempt_count = ?,
                    next_attempt_at = ?,
                    failure_code = ?
                WHERE id = ?
                """.formatted(outboxTable);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, event.publishedAt().map(Timestamp::from).orElse(null));
            ps.setString(2, event.status().name());
            ps.setInt(3, event.attemptCount());
            ps.setTimestamp(4, Timestamp.from(event.nextAttemptAt()));
            ps.setString(5, event.failureCode().orElse(null));
            ps.setObject(6, event.id());
            if (ps.executeUpdate() != 1) {
                throw new IllegalStateException("Outbox save failed for " + event.id());
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Outbox save failed for " + event.id(), ex);
        }
    }

    @Override
    public long countByStatus(OutboxStatus status) {
        String sql = "SELECT COUNT(*) FROM " + outboxTable + " WHERE status = ?";
        return queryCount(sql, status.name());
    }

    @Override
    public long countByTenantAndStatus(TenantId tenantId, OutboxStatus status) {
        String sql = "SELECT COUNT(*) FROM " + outboxTable + " WHERE tenant_id = ? AND status = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId.value());
            ps.setString(2, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Outbox countByTenantAndStatus failed", ex);
        }
    }

    @Override
    public List<OutboxEvent> findByStatus(OutboxStatus status, int limit) {
        String sql = "SELECT * FROM " + outboxTable + " WHERE status = ? ORDER BY next_attempt_at ASC LIMIT ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<OutboxEvent> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapOutbox(rs));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Outbox findByStatus failed", ex);
        }
    }

    private long queryCount(String sql, String status) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Outbox count failed", ex);
        }
    }

    private static void bindOutbox(PreparedStatement ps, OutboxEvent event) throws SQLException {
        ps.setObject(1, event.id());
        ps.setString(2, event.aggregateType());
        ps.setString(3, event.aggregateId());
        ps.setObject(4, event.tenantId().value());
        ps.setString(5, event.eventType());
        ps.setInt(6, event.eventVersion());
        ps.setString(7, event.payloadJson());
        ps.setObject(8, event.correlationId());
        ps.setObject(9, event.causationId().orElse(null));
        ps.setString(10, event.traceId().orElse(null));
        ps.setTimestamp(11, Timestamp.from(event.occurredAt()));
        ps.setTimestamp(12, event.publishedAt().map(Timestamp::from).orElse(null));
        ps.setString(13, event.status().name());
        ps.setInt(14, event.attemptCount());
        ps.setTimestamp(15, Timestamp.from(event.nextAttemptAt()));
        ps.setString(16, event.failureCode().orElse(null));
    }

    static OutboxEvent mapOutbox(ResultSet rs) throws SQLException {
        return new OutboxEvent(
                rs.getObject("id", UUID.class),
                rs.getString("aggregate_type"),
                rs.getString("aggregate_id"),
                TenantId.of(rs.getObject("tenant_id", UUID.class)),
                rs.getString("event_type"),
                rs.getInt("event_version"),
                rs.getString("payload_json"),
                rs.getObject("correlation_id", UUID.class),
                rs.getObject("causation_id", UUID.class),
                rs.getString("trace_id"),
                rs.getTimestamp("occurred_at").toInstant(),
                optionalInstant(rs, "published_at"),
                OutboxStatus.valueOf(rs.getString("status")),
                rs.getInt("attempt_count"),
                rs.getTimestamp("next_attempt_at").toInstant(),
                rs.getString("failure_code")
        );
    }

    private static Instant optionalInstant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }
}
