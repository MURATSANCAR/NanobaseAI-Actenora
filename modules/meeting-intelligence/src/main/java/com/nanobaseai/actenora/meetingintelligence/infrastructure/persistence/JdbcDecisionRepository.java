package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.DecisionRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Decision;
import com.nanobaseai.actenora.meetingintelligence.domain.model.HumanApprovalStatus;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcDecisionRepository implements DecisionRepository {

    private static final RowMapper<Decision> ROW_MAPPER = (rs, rowNum) -> Decision.rehydrate(
            rs.getObject("id", UUID.class),
            TenantId.of(rs.getObject("tenant_id", UUID.class)),
            rs.getObject("note_id", UUID.class),
            rs.getObject("note_version_id", UUID.class),
            rs.getString("text"),
            rs.getObject("supersedes_decision_id", UUID.class),
            rs.getObject("superseded_by_decision_id", UUID.class),
            rs.getBoolean("requires_manual_review"),
            (Double) rs.getObject("ai_confidence"),
            HumanApprovalStatus.valueOf(rs.getString("human_approval_status")),
            rs.getString("rationale"),
            rs.getString("status"),
            JdbcInstant.get(rs, "created_at"),
            JdbcInstant.get(rs, "updated_at"),
            rs.getLong("version")
    );

    private final JdbcTemplate jdbc;

    public JdbcDecisionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public Decision save(Decision decision) {
        String sql = """
                INSERT INTO meetingintelligence.decisions (
                    id, tenant_id, note_id, note_version_id, text,
                    supersedes_decision_id, superseded_by_decision_id,
                    requires_manual_review, ai_confidence, human_approval_status,
                    rationale, status,
                    created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    text = EXCLUDED.text,
                    supersedes_decision_id = EXCLUDED.supersedes_decision_id,
                    superseded_by_decision_id = EXCLUDED.superseded_by_decision_id,
                    requires_manual_review = EXCLUDED.requires_manual_review,
                    human_approval_status = EXCLUDED.human_approval_status,
                    rationale = EXCLUDED.rationale,
                    status = EXCLUDED.status,
                    updated_at = EXCLUDED.updated_at,
                    version = EXCLUDED.version
                """;
        jdbc.update(sql,
                decision.id(),
                decision.tenantId().value(),
                decision.noteId(),
                decision.noteVersionId(),
                decision.text(),
                decision.supersedesDecisionId(),
                decision.supersededByDecisionId(),
                decision.requiresManualReview(),
                decision.aiConfidence(),
                decision.humanApprovalStatus().name(),
                decision.rationale(),
                decision.decisionStatus(),
                JdbcInstant.toTimestamp(decision.createdAt()),
                JdbcInstant.toTimestamp(decision.updatedAt()),
                decision.version()
        );
        return decision;
    }

    @Override
    public Optional<Decision> findByIdAndTenantId(UUID id, TenantId tenantId) {
        String sql = """
                SELECT id, tenant_id, note_id, note_version_id, text,
                       supersedes_decision_id, superseded_by_decision_id,
                       requires_manual_review, ai_confidence, human_approval_status,
                       rationale, status,
                       created_at, updated_at, version
                FROM meetingintelligence.decisions
                WHERE id = ? AND tenant_id = ?
                """;
        return jdbc.query(sql, ROW_MAPPER, id, tenantId.value()).stream().findFirst();
    }

    @Override
    public List<Decision> findByNoteId(UUID noteId, TenantId tenantId) {
        String sql = """
                SELECT id, tenant_id, note_id, note_version_id, text,
                       supersedes_decision_id, superseded_by_decision_id,
                       requires_manual_review, ai_confidence, human_approval_status,
                       rationale, status,
                       created_at, updated_at, version
                FROM meetingintelligence.decisions
                WHERE note_id = ? AND tenant_id = ?
                ORDER BY created_at
                """;
        return jdbc.query(sql, ROW_MAPPER, noteId, tenantId.value());
    }
}
