package com.nanobaseai.actenora.meeting.infrastructure.persistence;

import com.nanobaseai.actenora.meeting.application.port.MeetingSeriesRepository;
import com.nanobaseai.actenora.meeting.domain.exception.OptimisticLockConflictException;
import com.nanobaseai.actenora.meeting.domain.model.MeetingSeries;
import com.nanobaseai.actenora.meeting.domain.model.MeetingSeriesStatus;
import com.nanobaseai.actenora.meeting.domain.model.MeetingType;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Optional;
import java.util.UUID;

/** JDBC meeting series store ({@code meeting.meeting_series}). */
public final class JdbcMeetingSeriesRepository implements MeetingSeriesRepository {

    private static final String COLUMNS = """
            id, tenant_id, business_context_id, graph_series_master_id, organizer_user_id,
            title, meeting_type, status, created_at, updated_at, version
            """;

    private static final RowMapper<MeetingSeries> ROW_MAPPER = (rs, rowNum) -> MeetingSeries.rehydrate(
            rs.getObject("id", UUID.class),
            TenantId.of(rs.getObject("tenant_id", UUID.class)),
            rs.getObject("business_context_id", UUID.class),
            rs.getString("graph_series_master_id"),
            rs.getObject("organizer_user_id", UUID.class),
            rs.getString("title"),
            MeetingType.valueOf(rs.getString("meeting_type")),
            MeetingSeriesStatus.valueOf(rs.getString("status")),
            JdbcInstant.get(rs, "created_at"),
            JdbcInstant.get(rs, "updated_at"),
            rs.getLong("version")
    );

    private final JdbcTemplate jdbc;

    public JdbcMeetingSeriesRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public MeetingSeries save(MeetingSeries series) {
        if (series.version() == 0L) {
            insert(series);
            return series;
        }
        long previousVersion = series.version() - 1L;
        String sql = """
                UPDATE meeting.meeting_series SET
                    graph_series_master_id = ?, title = ?, meeting_type = ?, status = ?,
                    updated_at = ?, version = ?
                WHERE id = ? AND version = ?
                """;
        int updated = jdbc.update(sql,
                series.graphSeriesMasterId(),
                series.title(),
                series.meetingType().name(),
                series.status().name(),
                JdbcInstant.toTimestamp(series.updatedAt()),
                series.version(),
                series.id(),
                previousVersion
        );
        if (updated != 1) {
            throw new OptimisticLockConflictException(series.id(), previousVersion);
        }
        return series;
    }

    @Override
    public Optional<MeetingSeries> findByIdAndTenantId(UUID id, TenantId tenantId) {
        String sql = "SELECT " + COLUMNS + " FROM meeting.meeting_series WHERE id = ? AND tenant_id = ?";
        return jdbc.query(sql, ROW_MAPPER, id, tenantId.value()).stream().findFirst();
    }

    private void insert(MeetingSeries series) {
        String sql = """
                INSERT INTO meeting.meeting_series (
                    id, tenant_id, business_context_id, graph_series_master_id, organizer_user_id,
                    title, meeting_type, status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbc.update(sql,
                series.id(),
                series.tenantId().value(),
                series.businessContextId(),
                series.graphSeriesMasterId(),
                series.organizerUserId(),
                series.title(),
                series.meetingType().name(),
                series.status().name(),
                JdbcInstant.toTimestamp(series.createdAt()),
                JdbcInstant.toTimestamp(series.updatedAt()),
                series.version()
        );
    }
}
