package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.ActionItemRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ActionItem;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ActionItemStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.HumanApprovalStatus;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcActionItemRepository implements ActionItemRepository {

    private static final RowMapper<ActionItem> ROW_MAPPER = (rs, rowNum) -> {
        Date due = rs.getDate("due_date");
        return ActionItem.rehydrate(
                rs.getObject("id", UUID.class),
                TenantId.of(rs.getObject("tenant_id", UUID.class)),
                rs.getObject("note_id", UUID.class),
                rs.getObject("note_version_id", UUID.class),
                rs.getString("text"),
                rs.getString("owner"),
                due == null ? null : due.toLocalDate(),
                ActionItemStatus.valueOf(rs.getString("status")),
                rs.getBoolean("requires_manual_review"),
                (Double) rs.getObject("ai_confidence"),
                HumanApprovalStatus.valueOf(rs.getString("human_approval_status")),
                rs.getString("owner_type"),
                rs.getString("priority"),
                rs.getString("relative_date"),
                JdbcInstant.get(rs, "created_at"),
                JdbcInstant.get(rs, "updated_at"),
                rs.getLong("version")
        );
    };

    private final JdbcTemplate jdbc;

    public JdbcActionItemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public ActionItem save(ActionItem item) {
        String sql = """
                INSERT INTO meetingintelligence.action_items (
                    id, tenant_id, note_id, note_version_id, text, owner, due_date, status,
                    requires_manual_review, ai_confidence, human_approval_status,
                    owner_type, priority, relative_date,
                    created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    text = EXCLUDED.text,
                    owner = EXCLUDED.owner,
                    due_date = EXCLUDED.due_date,
                    status = EXCLUDED.status,
                    requires_manual_review = EXCLUDED.requires_manual_review,
                    human_approval_status = EXCLUDED.human_approval_status,
                    owner_type = EXCLUDED.owner_type,
                    priority = EXCLUDED.priority,
                    relative_date = EXCLUDED.relative_date,
                    updated_at = EXCLUDED.updated_at,
                    version = EXCLUDED.version
                """;
        jdbc.update(sql,
                item.id(),
                item.tenantId().value(),
                item.noteId(),
                item.noteVersionId(),
                item.text(),
                item.owner(),
                item.dueDate() == null ? null : Date.valueOf(item.dueDate()),
                item.status().name(),
                item.requiresManualReview(),
                item.aiConfidence(),
                item.humanApprovalStatus().name(),
                item.ownerType(),
                item.priority(),
                item.relativeDate(),
                JdbcInstant.toTimestamp(item.createdAt()),
                JdbcInstant.toTimestamp(item.updatedAt()),
                item.version()
        );
        return item;
    }

    @Override
    public Optional<ActionItem> findByIdAndTenantId(UUID id, TenantId tenantId) {
        String sql = """
                SELECT id, tenant_id, note_id, note_version_id, text, owner, due_date, status,
                       requires_manual_review, ai_confidence, human_approval_status,
                       owner_type, priority, relative_date,
                       created_at, updated_at, version
                FROM meetingintelligence.action_items
                WHERE id = ? AND tenant_id = ?
                """;
        return jdbc.query(sql, ROW_MAPPER, id, tenantId.value()).stream().findFirst();
    }

    @Override
    public List<ActionItem> findByNoteId(UUID noteId, TenantId tenantId) {
        String sql = """
                SELECT id, tenant_id, note_id, note_version_id, text, owner, due_date, status,
                       requires_manual_review, ai_confidence, human_approval_status,
                       owner_type, priority, relative_date,
                       created_at, updated_at, version
                FROM meetingintelligence.action_items
                WHERE note_id = ? AND tenant_id = ?
                ORDER BY created_at
                """;
        return jdbc.query(sql, ROW_MAPPER, noteId, tenantId.value());
    }

    @Override
    public List<ActionItem> findByTenantId(TenantId tenantId) {
        String sql = """
                SELECT id, tenant_id, note_id, note_version_id, text, owner, due_date, status,
                       requires_manual_review, ai_confidence, human_approval_status,
                       owner_type, priority, relative_date,
                       created_at, updated_at, version
                FROM meetingintelligence.action_items
                WHERE tenant_id = ?
                ORDER BY created_at
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId.value());
    }
}
