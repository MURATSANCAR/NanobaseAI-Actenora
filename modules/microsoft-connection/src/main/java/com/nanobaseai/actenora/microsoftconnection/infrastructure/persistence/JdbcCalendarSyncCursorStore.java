package com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence;

import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarSyncCursor;
import com.nanobaseai.actenora.microsoftconnection.application.port.CalendarSyncCursorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JdbcCalendarSyncCursorStore implements CalendarSyncCursorStore {

    private static final String COLUMNS = "tenant_id, user_id, delta_link, next_link, updated_at";

    private static final RowMapper<CalendarSyncCursor> ROW_MAPPER = (rs, rowNum) -> new CalendarSyncCursor(
            rs.getObject("tenant_id", UUID.class),
            rs.getString("user_id"),
            rs.getString("delta_link"),
            rs.getString("next_link"),
            rs.getTimestamp("updated_at").toInstant()
    );

    private final JdbcTemplate jdbc;

    public JdbcCalendarSyncCursorStore(JdbcTemplate jdbcTemplate) {
        this.jdbc = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public Optional<CalendarSyncCursor> find(UUID tenantId, String userId) {
        List<CalendarSyncCursor> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM microsoftconnection.calendar_sync_cursor WHERE tenant_id = ? AND user_id = ?",
                ROW_MAPPER,
                tenantId,
                userId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public void save(CalendarSyncCursor cursor) {
        Objects.requireNonNull(cursor, "cursor");
        jdbc.update(
                """
                        INSERT INTO microsoftconnection.calendar_sync_cursor (
                            tenant_id, user_id, delta_link, next_link, updated_at
                        ) VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (tenant_id, user_id) DO UPDATE SET
                            delta_link = EXCLUDED.delta_link,
                            next_link = EXCLUDED.next_link,
                            updated_at = EXCLUDED.updated_at
                        """,
                cursor.tenantId(),
                cursor.userId(),
                cursor.deltaLink(),
                cursor.nextLink(),
                Timestamp.from(cursor.updatedAt())
        );
    }

    @Override
    public void delete(UUID tenantId, String userId) {
        jdbc.update(
                "DELETE FROM microsoftconnection.calendar_sync_cursor WHERE tenant_id = ? AND user_id = ?",
                tenantId,
                userId
        );
    }
}
