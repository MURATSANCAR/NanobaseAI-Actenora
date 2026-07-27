package com.nanobaseai.actenora.template.infrastructure.persistence;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import com.nanobaseai.actenora.template.api.TemplateVersionId;
import com.nanobaseai.actenora.template.application.port.out.NoteTemplateLockRepository;
import com.nanobaseai.actenora.template.domain.NoteTemplateLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Optional;
import java.util.UUID;

public final class JdbcNoteTemplateLockRepository implements NoteTemplateLockRepository {

    private static final RowMapper<NoteTemplateLock> ROW_MAPPER = (rs, rowNum) -> new NoteTemplateLock(
            TenantId.of(rs.getObject("tenant_id", UUID.class)),
            rs.getObject("note_id", UUID.class),
            TemplateVersionId.of(rs.getObject("template_version_id", UUID.class)),
            JdbcInstant.get(rs, "locked_at")
    );

    private final JdbcTemplate jdbc;

    public JdbcNoteTemplateLockRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public void save(NoteTemplateLock lock) {
        String sql = """
                INSERT INTO template.note_template_lock (tenant_id, note_id, template_version_id, locked_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (tenant_id, note_id) DO UPDATE SET
                    template_version_id = EXCLUDED.template_version_id,
                    locked_at = EXCLUDED.locked_at
                """;
        jdbc.update(sql,
                lock.tenantId().value(),
                lock.noteId(),
                lock.templateVersionId().value(),
                JdbcInstant.toTimestamp(lock.lockedAt())
        );
    }

    @Override
    public Optional<NoteTemplateLock> find(TenantId tenantId, UUID noteId) {
        String sql = """
                SELECT tenant_id, note_id, template_version_id, locked_at
                FROM template.note_template_lock
                WHERE tenant_id = ? AND note_id = ?
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId.value(), noteId).stream().findFirst();
    }
}
