package com.nanobaseai.actenora.meeting.infrastructure.persistence;

import com.nanobaseai.actenora.meeting.application.port.MeetingOccurrenceRepository;
import com.nanobaseai.actenora.meeting.domain.exception.OptimisticLockConflictException;
import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrence;
import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrenceStatus;
import com.nanobaseai.actenora.meeting.domain.model.ProcessingPriority;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC meeting occurrence store ({@code meeting.meeting_occurrences}). */
public final class JdbcMeetingOccurrenceRepository implements MeetingOccurrenceRepository {

    private static final String COLUMNS = """
            id, tenant_id, meeting_series_id, business_context_id, graph_event_immutable_id,
            ical_uid, original_start_at, teams_meeting_id, chat_id, join_web_url, title,
            organizer_user_id, scheduled_start_at, scheduled_end_at, actual_start_at, actual_end_at,
            status, processing_priority, created_at, updated_at, version
            """;

    private static final RowMapper<MeetingOccurrence> ROW_MAPPER = (rs, rowNum) -> MeetingOccurrence.rehydrate(
            rs.getObject("id", UUID.class),
            TenantId.of(rs.getObject("tenant_id", UUID.class)),
            rs.getObject("meeting_series_id", UUID.class),
            rs.getObject("business_context_id", UUID.class),
            rs.getString("graph_event_immutable_id"),
            rs.getString("ical_uid"),
            JdbcInstant.get(rs, "original_start_at"),
            rs.getString("teams_meeting_id"),
            rs.getString("chat_id"),
            rs.getString("join_web_url"),
            rs.getString("title"),
            rs.getObject("organizer_user_id", UUID.class),
            JdbcInstant.get(rs, "scheduled_start_at"),
            JdbcInstant.get(rs, "scheduled_end_at"),
            JdbcInstant.get(rs, "actual_start_at"),
            JdbcInstant.get(rs, "actual_end_at"),
            MeetingOccurrenceStatus.valueOf(rs.getString("status")),
            ProcessingPriority.valueOf(rs.getString("processing_priority")),
            JdbcInstant.get(rs, "created_at"),
            JdbcInstant.get(rs, "updated_at"),
            rs.getLong("version")
    );

    private final JdbcTemplate jdbc;

    public JdbcMeetingOccurrenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public MeetingOccurrence save(MeetingOccurrence occurrence) {
        if (occurrence.version() == 0L) {
            insert(occurrence);
            return occurrence;
        }
        long previousVersion = occurrence.version() - 1L;
        String sql = """
                UPDATE meeting.meeting_occurrences SET
                    graph_event_immutable_id = ?, ical_uid = ?, original_start_at = ?,
                    teams_meeting_id = ?, chat_id = ?, join_web_url = ?, title = ?,
                    organizer_user_id = ?, scheduled_start_at = ?, scheduled_end_at = ?,
                    actual_start_at = ?, actual_end_at = ?, status = ?, processing_priority = ?,
                    updated_at = ?, version = ?
                WHERE id = ? AND version = ?
                """;
        int updated = jdbc.update(sql,
                occurrence.graphEventImmutableId(),
                occurrence.icalUid(),
                JdbcInstant.toTimestamp(occurrence.originalStartAt()),
                occurrence.teamsMeetingId(),
                occurrence.chatId(),
                occurrence.joinWebUrl(),
                occurrence.title(),
                occurrence.organizerUserId(),
                JdbcInstant.toTimestamp(occurrence.scheduledStartAt()),
                JdbcInstant.toTimestamp(occurrence.scheduledEndAt()),
                JdbcInstant.toTimestamp(occurrence.actualStartAt()),
                JdbcInstant.toTimestamp(occurrence.actualEndAt()),
                occurrence.status().name(),
                occurrence.processingPriority().name(),
                JdbcInstant.toTimestamp(occurrence.updatedAt()),
                occurrence.version(),
                occurrence.id(),
                previousVersion
        );
        if (updated != 1) {
            throw new OptimisticLockConflictException(occurrence.id(), previousVersion);
        }
        return occurrence;
    }

    @Override
    public Optional<MeetingOccurrence> findByIdAndTenantId(UUID id, TenantId tenantId) {
        String sql = "SELECT " + COLUMNS + " FROM meeting.meeting_occurrences WHERE id = ? AND tenant_id = ?";
        return jdbc.query(sql, ROW_MAPPER, id, tenantId.value()).stream().findFirst();
    }

    @Override
    public boolean existsById(UUID id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM meeting.meeting_occurrences WHERE id = ?",
                Integer.class,
                id
        );
        return count != null && count > 0;
    }

    @Override
    public Optional<MeetingOccurrence> findByTenantIdAndGraphEventImmutableId(
            TenantId tenantId, String graphEventImmutableId) {
        String sql = "SELECT " + COLUMNS + """
                 FROM meeting.meeting_occurrences
                 WHERE tenant_id = ? AND graph_event_immutable_id = ?
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId.value(), graphEventImmutableId).stream().findFirst();
    }

    @Override
    public boolean existsByTenantIdAndGraphEventImmutableId(TenantId tenantId, String graphEventImmutableId) {
        Integer count = jdbc.queryForObject(
                """
                        SELECT COUNT(*) FROM meeting.meeting_occurrences
                        WHERE tenant_id = ? AND graph_event_immutable_id = ?
                        """,
                Integer.class,
                tenantId.value(),
                graphEventImmutableId
        );
        return count != null && count > 0;
    }

    @Override
    public boolean existsByTenantIdAndIcalUidAndOriginalStartAt(
            TenantId tenantId, String icalUid, Instant originalStartAt) {
        Integer count = jdbc.queryForObject(
                """
                        SELECT COUNT(*) FROM meeting.meeting_occurrences
                        WHERE tenant_id = ? AND ical_uid = ? AND original_start_at = ?
                        """,
                Integer.class,
                tenantId.value(),
                icalUid,
                JdbcInstant.toTimestamp(originalStartAt)
        );
        return count != null && count > 0;
    }

    @Override
    public PageResult<MeetingOccurrence> findByTenant(
            TenantId tenantId,
            MeetingOccurrenceStatus status,
            UUID businessContextId,
            String cursor,
            int limit
    ) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM meeting.meeting_occurrences WHERE tenant_id = ?");
        List<Object> args = new java.util.ArrayList<>();
        args.add(tenantId.value());
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status.name());
        }
        if (businessContextId != null) {
            sql.append(" AND business_context_id = ?");
            args.add(businessContextId);
        }
        if (cursor != null && !cursor.isBlank()) {
            sql.append(" AND id > ?");
            args.add(UUID.fromString(cursor));
        }
        sql.append(" ORDER BY created_at ASC, id ASC LIMIT ?");
        args.add(limit + 1);
        List<MeetingOccurrence> rows = jdbc.query(sql.toString(), ROW_MAPPER, args.toArray());
        String next = rows.size() > limit ? rows.get(limit - 1).id().toString() : null;
        List<MeetingOccurrence> page = rows.size() > limit ? rows.subList(0, limit) : rows;
        return new PageResult<>(page, next);
    }

    private void insert(MeetingOccurrence occurrence) {
        String sql = """
                INSERT INTO meeting.meeting_occurrences (
                    id, tenant_id, meeting_series_id, business_context_id, graph_event_immutable_id,
                    ical_uid, original_start_at, teams_meeting_id, chat_id, join_web_url, title,
                    organizer_user_id, scheduled_start_at, scheduled_end_at, actual_start_at, actual_end_at,
                    status, processing_priority, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbc.update(sql,
                occurrence.id(),
                occurrence.tenantId().value(),
                occurrence.meetingSeriesId(),
                occurrence.businessContextId(),
                occurrence.graphEventImmutableId(),
                occurrence.icalUid(),
                JdbcInstant.toTimestamp(occurrence.originalStartAt()),
                occurrence.teamsMeetingId(),
                occurrence.chatId(),
                occurrence.joinWebUrl(),
                occurrence.title(),
                occurrence.organizerUserId(),
                JdbcInstant.toTimestamp(occurrence.scheduledStartAt()),
                JdbcInstant.toTimestamp(occurrence.scheduledEndAt()),
                JdbcInstant.toTimestamp(occurrence.actualStartAt()),
                JdbcInstant.toTimestamp(occurrence.actualEndAt()),
                occurrence.status().name(),
                occurrence.processingPriority().name(),
                JdbcInstant.toTimestamp(occurrence.createdAt()),
                JdbcInstant.toTimestamp(occurrence.updatedAt()),
                occurrence.version()
        );
    }
}
