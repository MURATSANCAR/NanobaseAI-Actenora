package com.nanobaseai.actenora.approval.infrastructure.persistence;

import com.nanobaseai.actenora.approval.application.port.ParticipantDisputeRepository;
import com.nanobaseai.actenora.approval.domain.DisputeStatus;
import com.nanobaseai.actenora.approval.domain.ParticipantDispute;
import com.nanobaseai.actenora.approval.api.ApprovalSubjectType;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC participant dispute store ({@code approval.participant_disputes}). */
public final class JdbcParticipantDisputeRepository implements ParticipantDisputeRepository {

    private static final RowMapper<ParticipantDispute> ROW_MAPPER = (rs, rowNum) -> ParticipantDispute.rehydrate(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("subject_id", UUID.class),
            ApprovalSubjectType.valueOf(rs.getString("subject_type")),
            rs.getString("participant_id"),
            rs.getString("proposed_content"),
            rs.getString("reason"),
            DisputeStatus.valueOf(rs.getString("status")),
            JdbcInstant.get(rs, "created_at"),
            JdbcInstant.get(rs, "resolved_at"),
            rs.getString("resolved_by")
    );

    private final JdbcTemplate jdbc;

    public JdbcParticipantDisputeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public ParticipantDispute save(ParticipantDispute dispute) {
        String sql = """
                INSERT INTO approval.participant_disputes (
                    id, tenant_id, subject_id, subject_type, participant_id, proposed_content,
                    reason, status, created_at, resolved_at, resolved_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status,
                    resolved_at = EXCLUDED.resolved_at,
                    resolved_by = EXCLUDED.resolved_by
                """;
        jdbc.update(sql,
                dispute.id(),
                dispute.tenantId(),
                dispute.subjectId(),
                dispute.subjectType().name(),
                dispute.participantId(),
                dispute.proposedContent(),
                dispute.reason(),
                dispute.status().name(),
                JdbcInstant.toTimestamp(dispute.createdAt()),
                JdbcInstant.toTimestamp(dispute.resolvedAt()),
                dispute.resolvedBy()
        );
        return dispute;
    }

    @Override
    public Optional<ParticipantDispute> findById(UUID tenantId, UUID disputeId) {
        String sql = """
                SELECT id, tenant_id, subject_id, subject_type, participant_id, proposed_content,
                       reason, status, created_at, resolved_at, resolved_by
                FROM approval.participant_disputes WHERE id = ? AND tenant_id = ?
                """;
        return jdbc.query(sql, ROW_MAPPER, disputeId, tenantId).stream().findFirst();
    }

    @Override
    public List<ParticipantDispute> findBySubject(UUID tenantId, UUID subjectId) {
        String sql = """
                SELECT id, tenant_id, subject_id, subject_type, participant_id, proposed_content,
                       reason, status, created_at, resolved_at, resolved_by
                FROM approval.participant_disputes WHERE tenant_id = ? AND subject_id = ?
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId, subjectId);
    }
}
