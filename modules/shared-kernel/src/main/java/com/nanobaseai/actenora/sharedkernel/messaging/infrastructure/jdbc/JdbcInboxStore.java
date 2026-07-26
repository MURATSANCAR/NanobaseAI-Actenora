package com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.jdbc;

import com.nanobaseai.actenora.sharedkernel.messaging.InboxEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.InboxStatus;
import com.nanobaseai.actenora.sharedkernel.messaging.port.InboxStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC-backed consumer inbox ({@code <schema>.inbox_event}).
 */
public final class JdbcInboxStore implements InboxStore {

    private final DataSource dataSource;
    private final String inboxTable;

    public JdbcInboxStore(DataSource dataSource, String schema) {
        this.dataSource = dataSource;
        this.inboxTable = JdbcMessagingSchema.table(schema, "inbox_event");
    }

    @Override
    public ClaimResult claim(InboxEvent event) {
        String insertSql = """
                INSERT INTO %s (consumer_name, event_id, received_at, processed_at, status, failure_code)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (consumer_name, event_id) DO NOTHING
                """.formatted(inboxTable);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, event.consumerName());
            ps.setObject(2, event.eventId());
            ps.setTimestamp(3, Timestamp.from(event.receivedAt()));
            ps.setTimestamp(4, event.processedAt().map(Timestamp::from).orElse(null));
            ps.setString(5, event.status().name());
            ps.setString(6, event.failureCode().orElse(null));
            int inserted = ps.executeUpdate();
            InboxEvent stored = find(event.consumerName(), event.eventId()).orElseThrow();
            ClaimOutcome outcome = inserted == 1 ? ClaimOutcome.INSERTED : ClaimOutcome.DUPLICATE;
            return new ClaimResult(outcome, stored);
        } catch (SQLException ex) {
            throw new IllegalStateException("Inbox claim failed for " + event.eventId(), ex);
        }
    }

    @Override
    public Optional<InboxEvent> find(String consumerName, UUID eventId) {
        String sql = "SELECT * FROM " + inboxTable + " WHERE consumer_name = ? AND event_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, consumerName);
            ps.setObject(2, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapInbox(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Inbox find failed", ex);
        }
    }

    @Override
    public void save(InboxEvent event) {
        String sql = """
                UPDATE %s SET
                    processed_at = ?,
                    status = ?,
                    failure_code = ?
                WHERE consumer_name = ? AND event_id = ?
                """.formatted(inboxTable);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, event.processedAt().map(Timestamp::from).orElse(null));
            ps.setString(2, event.status().name());
            ps.setString(3, event.failureCode().orElse(null));
            ps.setString(4, event.consumerName());
            ps.setObject(5, event.eventId());
            if (ps.executeUpdate() != 1) {
                throw new IllegalStateException("Inbox save failed for " + event.eventId());
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Inbox save failed for " + event.eventId(), ex);
        }
    }

    static InboxEvent mapInbox(ResultSet rs) throws SQLException {
        return new InboxEvent(
                rs.getString("consumer_name"),
                rs.getObject("event_id", UUID.class),
                rs.getTimestamp("received_at").toInstant(),
                optionalInstant(rs, "processed_at"),
                InboxStatus.valueOf(rs.getString("status")),
                rs.getString("failure_code")
        );
    }

    private static Instant optionalInstant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }
}
