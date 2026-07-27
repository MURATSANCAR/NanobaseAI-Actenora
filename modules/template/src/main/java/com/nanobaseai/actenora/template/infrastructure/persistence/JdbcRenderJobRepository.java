package com.nanobaseai.actenora.template.infrastructure.persistence;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import com.nanobaseai.actenora.template.api.RenderJobId;
import com.nanobaseai.actenora.template.api.RenderedDocumentId;
import com.nanobaseai.actenora.template.api.TemplateVersionId;
import com.nanobaseai.actenora.template.application.port.out.RenderJobRepository;
import com.nanobaseai.actenora.template.domain.ContentHash;
import com.nanobaseai.actenora.template.domain.RenderFormat;
import com.nanobaseai.actenora.template.domain.RenderJob;
import com.nanobaseai.actenora.template.domain.RenderJobStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcRenderJobRepository implements RenderJobRepository {

    private static final RowMapper<RenderJob> ROW_MAPPER = (rs, rowNum) -> {
        UUID renderedDoc = rs.getObject("rendered_document_id", UUID.class);
        return RenderJob.builder()
                .id(RenderJobId.of(rs.getObject("id", UUID.class)))
                .tenantId(TenantId.of(rs.getObject("tenant_id", UUID.class)))
                .noteId(rs.getObject("note_id", UUID.class))
                .templateVersionId(TemplateVersionId.of(rs.getObject("template_version_id", UUID.class)))
                .format(RenderFormat.valueOf(rs.getString("format")))
                .contentHash(new ContentHash(rs.getString("content_hash_sha256").toLowerCase()))
                .contentJson(rs.getString("content_json"))
                .status(RenderJobStatus.valueOf(rs.getString("status")))
                .attemptCount(rs.getInt("attempt_count"))
                .maxAttempts(rs.getInt("max_attempts"))
                .lastError(rs.getString("last_error"))
                .renderedDocumentId(renderedDoc == null ? null : RenderedDocumentId.of(renderedDoc))
                .createdAt(JdbcInstant.get(rs, "created_at"))
                .updatedAt(JdbcInstant.get(rs, "updated_at"))
                .completedAt(JdbcInstant.get(rs, "completed_at"))
                .build();
    };

    private final JdbcTemplate jdbc;

    public JdbcRenderJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public void save(RenderJob job) {
        String sql = """
                INSERT INTO template.render_job (
                    id, tenant_id, note_id, template_version_id, format, content_hash_sha256, content_json,
                    status, attempt_count, max_attempts, last_error, rendered_document_id,
                    created_at, updated_at, completed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status,
                    attempt_count = EXCLUDED.attempt_count,
                    max_attempts = EXCLUDED.max_attempts,
                    last_error = EXCLUDED.last_error,
                    rendered_document_id = EXCLUDED.rendered_document_id,
                    updated_at = EXCLUDED.updated_at,
                    completed_at = EXCLUDED.completed_at
                """;
        jdbc.update(sql,
                job.id().value(),
                job.tenantId().value(),
                job.noteId(),
                job.templateVersionId().value(),
                job.format().name(),
                job.contentHash().sha256Hex(),
                job.contentJson(),
                job.status().name(),
                job.attemptCount(),
                job.maxAttempts(),
                job.lastError().orElse(null),
                job.renderedDocumentId().map(RenderedDocumentId::value).orElse(null),
                JdbcInstant.toTimestamp(job.createdAt()),
                JdbcInstant.toTimestamp(job.updatedAt()),
                job.completedAt().map(JdbcInstant::toTimestamp).orElse(null)
        );
    }

    @Override
    public Optional<RenderJob> findById(TenantId tenantId, RenderJobId id) {
        String sql = """
                SELECT id, tenant_id, note_id, template_version_id, format, content_hash_sha256, content_json,
                       status, attempt_count, max_attempts, last_error, rendered_document_id,
                       created_at, updated_at, completed_at
                FROM template.render_job
                WHERE tenant_id = ? AND id = ?
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId.value(), id.value()).stream().findFirst();
    }

    @Override
    public Optional<RenderJob> findByContentHash(TenantId tenantId, ContentHash contentHash) {
        String sql = """
                SELECT id, tenant_id, note_id, template_version_id, format, content_hash_sha256, content_json,
                       status, attempt_count, max_attempts, last_error, rendered_document_id,
                       created_at, updated_at, completed_at
                FROM template.render_job
                WHERE tenant_id = ? AND content_hash_sha256 = ?
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId.value(), contentHash.sha256Hex()).stream().findFirst();
    }

    @Override
    public List<RenderJob> findPending(int limit) {
        String sql = """
                SELECT id, tenant_id, note_id, template_version_id, format, content_hash_sha256, content_json,
                       status, attempt_count, max_attempts, last_error, rendered_document_id,
                       created_at, updated_at, completed_at
                FROM template.render_job
                WHERE status = 'PENDING'
                ORDER BY created_at
                LIMIT ?
                """;
        return jdbc.query(sql, ROW_MAPPER, limit);
    }

    @Override
    public List<RenderJob> findByNoteId(TenantId tenantId, UUID noteId) {
        String sql = """
                SELECT id, tenant_id, note_id, template_version_id, format, content_hash_sha256, content_json,
                       status, attempt_count, max_attempts, last_error, rendered_document_id,
                       created_at, updated_at, completed_at
                FROM template.render_job
                WHERE tenant_id = ? AND note_id = ?
                ORDER BY created_at DESC
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId.value(), noteId);
    }
}
