package com.nanobaseai.actenora.meeting.infrastructure.persistence;

import com.nanobaseai.actenora.meeting.application.port.MeetingParticipantRepository;
import com.nanobaseai.actenora.meeting.domain.model.AttendanceStatus;
import com.nanobaseai.actenora.meeting.domain.model.MeetingParticipant;
import com.nanobaseai.actenora.meeting.domain.model.ParticipantType;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

/** JDBC meeting participant store ({@code meeting.meeting_participants}). */
public final class JdbcMeetingParticipantRepository implements MeetingParticipantRepository {

    private static final String COLUMNS = """
            id, tenant_id, meeting_occurrence_id, entra_user_id, display_name, email,
            participant_type, attendance_status, joined_at, left_at, is_external
            """;

    private static final RowMapper<MeetingParticipant> ROW_MAPPER = (rs, rowNum) -> MeetingParticipant.rehydrate(
            rs.getObject("id", UUID.class),
            TenantId.of(rs.getObject("tenant_id", UUID.class)),
            rs.getObject("meeting_occurrence_id", UUID.class),
            rs.getString("entra_user_id"),
            rs.getString("display_name"),
            rs.getString("email"),
            ParticipantType.valueOf(rs.getString("participant_type")),
            AttendanceStatus.valueOf(rs.getString("attendance_status")),
            JdbcInstant.get(rs, "joined_at"),
            JdbcInstant.get(rs, "left_at"),
            rs.getBoolean("is_external")
    );

    private final JdbcTemplate jdbc;

    public JdbcMeetingParticipantRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public MeetingParticipant save(MeetingParticipant participant) {
        String sql = """
                INSERT INTO meeting.meeting_participants (
                    id, tenant_id, meeting_occurrence_id, entra_user_id, display_name, email,
                    participant_type, attendance_status, joined_at, left_at, is_external
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    entra_user_id = EXCLUDED.entra_user_id,
                    display_name = EXCLUDED.display_name,
                    email = EXCLUDED.email,
                    participant_type = EXCLUDED.participant_type,
                    attendance_status = EXCLUDED.attendance_status,
                    joined_at = EXCLUDED.joined_at,
                    left_at = EXCLUDED.left_at,
                    is_external = EXCLUDED.is_external
                """;
        jdbc.update(sql,
                participant.id(),
                participant.tenantId().value(),
                participant.meetingOccurrenceId(),
                participant.entraUserId(),
                participant.displayName(),
                participant.email(),
                participant.participantType().name(),
                participant.attendanceStatus().name(),
                JdbcInstant.toTimestamp(participant.joinedAt()),
                JdbcInstant.toTimestamp(participant.leftAt()),
                participant.isExternal()
        );
        return participant;
    }

    @Override
    public List<MeetingParticipant> findByMeetingOccurrenceIdAndTenantId(UUID meetingOccurrenceId, TenantId tenantId) {
        String sql = "SELECT " + COLUMNS + """
                 FROM meeting.meeting_participants
                 WHERE meeting_occurrence_id = ? AND tenant_id = ?
                """;
        return jdbc.query(sql, ROW_MAPPER, meetingOccurrenceId, tenantId.value());
    }
}
