package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingNoteRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.exception.OptimisticLockConflictException;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNote;
import com.nanobaseai.actenora.meetingintelligence.domain.model.NoteReviewStatus;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Optional;
import java.util.UUID;

/** JDBC meeting note store ({@code meetingintelligence.meeting_notes}). */
public final class JdbcMeetingNoteRepository implements MeetingNoteRepository {

    private static final RowMapper<MeetingNote> ROW_MAPPER = (rs, rowNum) -> MeetingNote.rehydrate(
            rs.getObject("id", UUID.class),
            TenantId.of(rs.getObject("tenant_id", UUID.class)),
            rs.getObject("meeting_occurrence_id", UUID.class),
            rs.getObject("current_version_id", UUID.class),
            rs.getInt("current_version_number"),
            NoteReviewStatus.valueOf(rs.getString("review_status")),
            JdbcInstant.get(rs, "created_at"),
            JdbcInstant.get(rs, "updated_at"),
            rs.getLong("version")
    );

    private final JdbcTemplate jdbc;

    public JdbcMeetingNoteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public MeetingNote save(MeetingNote note) {
        if (note.version() == 0L) {
            insert(note);
            return note;
        }
        long previousVersion = note.version() - 1L;
        String sql = """
                UPDATE meetingintelligence.meeting_notes SET
                    current_version_id = ?, current_version_number = ?, review_status = ?,
                    updated_at = ?, version = ?
                WHERE id = ? AND tenant_id = ? AND version = ?
                """;
        int updated = jdbc.update(sql,
                note.currentVersionId(),
                note.currentVersionNumber(),
                note.reviewStatus().name(),
                JdbcInstant.toTimestamp(note.updatedAt()),
                note.version(),
                note.id(),
                note.tenantId().value(),
                previousVersion
        );
        if (updated != 1) {
            throw new OptimisticLockConflictException(note.id(), previousVersion);
        }
        return note;
    }

    @Override
    public Optional<MeetingNote> findByIdAndTenantId(UUID id, TenantId tenantId) {
        String sql = """
                SELECT id, tenant_id, meeting_occurrence_id, current_version_id, current_version_number,
                       review_status, created_at, updated_at, version
                FROM meetingintelligence.meeting_notes
                WHERE id = ? AND tenant_id = ?
                """;
        return jdbc.query(sql, ROW_MAPPER, id, tenantId.value()).stream().findFirst();
    }

    private void insert(MeetingNote note) {
        String sql = """
                INSERT INTO meetingintelligence.meeting_notes (
                    id, tenant_id, meeting_occurrence_id, current_version_id, current_version_number,
                    review_status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbc.update(sql,
                note.id(),
                note.tenantId().value(),
                note.meetingOccurrenceId(),
                note.currentVersionId(),
                note.currentVersionNumber(),
                note.reviewStatus().name(),
                JdbcInstant.toTimestamp(note.createdAt()),
                JdbcInstant.toTimestamp(note.updatedAt()),
                note.version()
        );
    }
}
