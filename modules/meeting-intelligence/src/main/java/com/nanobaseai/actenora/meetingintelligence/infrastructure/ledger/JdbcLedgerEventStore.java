package com.nanobaseai.actenora.meetingintelligence.infrastructure.ledger;

import com.nanobaseai.actenora.meetingintelligence.application.ledger.port.LedgerEventStore;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.event.LedgerEvent;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.event.LedgerEventType;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcJson;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Append-only JDBC ledger event store ({@code meetingintelligence.ledger_events}). */
public final class JdbcLedgerEventStore implements LedgerEventStore {

    private static final RowMapper<LedgerEvent> ROW_MAPPER = (rs, rowNum) -> LedgerEvent.rehydrate(
            rs.getObject("event_id", UUID.class),
            TenantId.of(rs.getObject("tenant_id", UUID.class)),
            LedgerEventType.valueOf(rs.getString("event_type")),
            rs.getString("aggregate_type"),
            rs.getObject("aggregate_id", UUID.class),
            rs.getObject("meeting_occurrence_id", UUID.class),
            JdbcInstant.get(rs, "occurred_at"),
            rs.getLong("sequence_no"),
            JdbcJson.read(rs.getString("payload_json"), PayloadDto.class) == null
                    ? Map.of()
                    : JdbcJson.read(rs.getString("payload_json"), PayloadDto.class).values()
    );

    private final JdbcTemplate jdbc;

    public JdbcLedgerEventStore(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public LedgerEvent append(LedgerEvent event) {
        String sql = """
                INSERT INTO meetingintelligence.ledger_events (
                    event_id, tenant_id, event_type, aggregate_type, aggregate_id,
                    meeting_occurrence_id, occurred_at, sequence_no, payload_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try {
            jdbc.update(sql,
                    event.eventId(),
                    event.tenantId().value(),
                    event.type().name(),
                    event.aggregateType(),
                    event.aggregateId(),
                    event.meetingOccurrenceId(),
                    JdbcInstant.toTimestamp(event.occurredAt()),
                    event.sequence(),
                    JdbcJson.write(new PayloadDto(event.payload()))
            );
        } catch (DuplicateKeyException ex) {
            throw new IllegalStateException("Ledger event already exists: " + event.eventId(), ex);
        }
        return event;
    }

    @Override
    public List<LedgerEvent> findAllByTenant(TenantId tenantId) {
        String sql = """
                SELECT event_id, tenant_id, event_type, aggregate_type, aggregate_id,
                       meeting_occurrence_id, occurred_at, sequence_no, payload_json
                FROM meetingintelligence.ledger_events
                WHERE tenant_id = ?
                ORDER BY sequence_no ASC
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId.value()).stream()
                .sorted(Comparator.comparingLong(LedgerEvent::sequence))
                .toList();
    }

    @Override
    public long nextSequence(TenantId tenantId) {
        Long max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sequence_no), 0) FROM meetingintelligence.ledger_events WHERE tenant_id = ?",
                Long.class,
                tenantId.value()
        );
        return max == null ? 1L : max + 1L;
    }

    private record PayloadDto(Map<String, String> values) {
    }
}
