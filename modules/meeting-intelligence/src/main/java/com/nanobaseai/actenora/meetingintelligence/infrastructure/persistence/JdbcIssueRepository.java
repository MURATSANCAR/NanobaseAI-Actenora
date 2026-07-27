package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.IssueRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.model.HumanApprovalStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Issue;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcIssueRepository implements IssueRepository {

    private static final RowMapper<Issue> ROW_MAPPER = (rs, rowNum) -> Issue.rehydrate(
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

    public JdbcIssueRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public Issue save(Issue entity) {
        String sql = """
                INSERT INTO meetingintelligence.issues (
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
                entity.id(),
                entity.tenantId().value(),
                entity.noteId(),
                entity.noteVersionId(),
                entity.text(),
                entity.requiresManualReview(),
                entity.aiConfidence(),
                entity.humanApprovalStatus().name(),
                JdbcInstant.toTimestamp(entity.createdAt()),
                JdbcInstant.toTimestamp(entity.updatedAt()),
                entity.version()
        );
        return entity;
    }

    @Override
    public Optional<Issue> findByIdAndTenantId(UUID id, TenantId tenantId) {
        String sql = """
                SELECT id, tenant_id, note_id, note_version_id, text,
                       requires_manual_review, ai_confidence, human_approval_status,
                       created_at, updated_at, version
                FROM meetingintelligence.issues
                WHERE id = ? AND tenant_id = ?
                """;
        return jdbc.query(sql, ROW_MAPPER, id, tenantId.value()).stream().findFirst();
    }

    @Override
    public List<Issue> findByNoteId(UUID noteId, TenantId tenantId) {
        String sql = """
                SELECT id, tenant_id, note_id, note_version_id, text,
                       requires_manual_review, ai_confidence, human_approval_status,
                       created_at, updated_at, version
                FROM meetingintelligence.issues
                WHERE note_id = ? AND tenant_id = ?
                ORDER BY created_at
                """;
        return jdbc.query(sql, ROW_MAPPER, noteId, tenantId.value());
    }
}
