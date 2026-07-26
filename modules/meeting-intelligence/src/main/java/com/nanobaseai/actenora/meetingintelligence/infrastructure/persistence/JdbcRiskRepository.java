package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.RiskRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.model.HumanApprovalStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Risk;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcRiskRepository implements RiskRepository {

    private static final RowMapper<Risk> ROW_MAPPER = (rs, rowNum) -> Risk.rehydrate(
            rs.getObject("id", UUID.class),
            TenantId.of(rs.getObject("tenant_id", UUID.class)),
            rs.getObject("note_id", UUID.class),
            rs.getObject("note_version_id", UUID.class),
            rs.getString("text"),
            rs.getBoolean("requires_manual_review"),
            (Double) rs.getObject("ai_confidence"),
            HumanApprovalStatus.valueOf(rs.getString("human_approval_status")),
            JdbcInstant.get(rs, "created_at"),
            JdbcInstant.get(rs, "updated_at"),
            rs.getLong("version")
    );

    private final JdbcTemplate jdbc;

    public JdbcRiskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public Risk save(Risk risk) {
        String sql = """
                INSERT INTO meetingintelligence.risks (
                    id, tenant_id, note_id, note_version_id, text,
                    requires_manual_review, ai_confidence, human_approval_status,
                    created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    text = EXCLUDED.text,
                    requires_manual_review = EXCLUDED.requires_manual_review,
                    human_approval_status = EXCLUDED.human_approval_status,
                    updated_at = EXCLUDED.updated_at,
                    version = EXCLUDED.version
                """;
        jdbc.update(sql,
                risk.id(),
                risk.tenantId().value(),
                risk.noteId(),
                risk.noteVersionId(),
                risk.text(),
                risk.requiresManualReview(),
                risk.aiConfidence(),
                risk.humanApprovalStatus().name(),
                JdbcInstant.toTimestamp(risk.createdAt()),
                JdbcInstant.toTimestamp(risk.updatedAt()),
                risk.version()
        );
        return risk;
    }

    @Override
    public Optional<Risk> findByIdAndTenantId(UUID id, TenantId tenantId) {
        String sql = """
                SELECT id, tenant_id, note_id, note_version_id, text,
                       requires_manual_review, ai_confidence, human_approval_status,
                       created_at, updated_at, version
                FROM meetingintelligence.risks
                WHERE id = ? AND tenant_id = ?
                """;
        return jdbc.query(sql, ROW_MAPPER, id, tenantId.value()).stream().findFirst();
    }

    @Override
    public List<Risk> findByNoteId(UUID noteId, TenantId tenantId) {
        String sql = """
                SELECT id, tenant_id, note_id, note_version_id, text,
                       requires_manual_review, ai_confidence, human_approval_status,
                       created_at, updated_at, version
                FROM meetingintelligence.risks
                WHERE note_id = ? AND tenant_id = ?
                ORDER BY created_at
                """;
        return jdbc.query(sql, ROW_MAPPER, noteId, tenantId.value());
    }
}
