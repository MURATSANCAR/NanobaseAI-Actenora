package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.CommitmentRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Commitment;
import com.nanobaseai.actenora.meetingintelligence.domain.model.CommitmentConfirmationStatus;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcCommitmentRepository implements CommitmentRepository {

    private static final RowMapper<Commitment> ROW_MAPPER = (rs, rowNum) -> Commitment.rehydrate(
            rs.getObject("id", UUID.class),
            TenantId.of(rs.getObject("tenant_id", UUID.class)),
            rs.getObject("note_id", UUID.class),
            rs.getObject("note_version_id", UUID.class),
            rs.getString("text"),
            rs.getString("owner"),
            CommitmentConfirmationStatus.valueOf(rs.getString("confirmation_status")),
            rs.getBoolean("requires_manual_review"),
            (Double) rs.getObject("ai_confidence"),
            JdbcInstant.get(rs, "created_at"),
            JdbcInstant.get(rs, "updated_at"),
            JdbcInstant.get(rs, "decided_at"),
            rs.getObject("decided_by_user_id", UUID.class),
            rs.getLong("version")
    );

    private final JdbcTemplate jdbc;

    public JdbcCommitmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public Commitment save(Commitment commitment) {
        String sql = """
                INSERT INTO meetingintelligence.commitments (
                    id, tenant_id, note_id, note_version_id, text, owner, confirmation_status,
                    requires_manual_review, ai_confidence, created_at, updated_at,
                    decided_at, decided_by_user_id, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    text = EXCLUDED.text,
                    owner = EXCLUDED.owner,
                    confirmation_status = EXCLUDED.confirmation_status,
                    requires_manual_review = EXCLUDED.requires_manual_review,
                    updated_at = EXCLUDED.updated_at,
                    decided_at = EXCLUDED.decided_at,
                    decided_by_user_id = EXCLUDED.decided_by_user_id,
                    version = EXCLUDED.version
                """;
        jdbc.update(sql,
                commitment.id(),
                commitment.tenantId().value(),
                commitment.noteId(),
                commitment.noteVersionId(),
                commitment.text(),
                commitment.owner(),
                commitment.confirmationStatus().name(),
                commitment.requiresManualReview(),
                commitment.aiConfidence(),
                JdbcInstant.toTimestamp(commitment.createdAt()),
                JdbcInstant.toTimestamp(commitment.updatedAt()),
                JdbcInstant.toTimestamp(commitment.decidedAt()),
                commitment.decidedByUserId(),
                commitment.version()
        );
        return commitment;
    }

    @Override
    public Optional<Commitment> findByIdAndTenantId(UUID id, TenantId tenantId) {
        String sql = """
                SELECT id, tenant_id, note_id, note_version_id, text, owner, confirmation_status,
                       requires_manual_review, ai_confidence, created_at, updated_at,
                       decided_at, decided_by_user_id, version
                FROM meetingintelligence.commitments
                WHERE id = ? AND tenant_id = ?
                """;
        return jdbc.query(sql, ROW_MAPPER, id, tenantId.value()).stream().findFirst();
    }

    @Override
    public List<Commitment> findByNoteId(UUID noteId, TenantId tenantId) {
        String sql = """
                SELECT id, tenant_id, note_id, note_version_id, text, owner, confirmation_status,
                       requires_manual_review, ai_confidence, created_at, updated_at,
                       decided_at, decided_by_user_id, version
                FROM meetingintelligence.commitments
                WHERE note_id = ? AND tenant_id = ?
                ORDER BY created_at
                """;
        return jdbc.query(sql, ROW_MAPPER, noteId, tenantId.value());
    }
}
