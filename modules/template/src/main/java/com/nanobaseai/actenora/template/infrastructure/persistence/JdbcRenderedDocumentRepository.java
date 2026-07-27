package com.nanobaseai.actenora.template.infrastructure.persistence;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import com.nanobaseai.actenora.template.api.RenderJobId;
import com.nanobaseai.actenora.template.api.RenderedDocumentId;
import com.nanobaseai.actenora.template.api.TemplateVersionId;
import com.nanobaseai.actenora.template.application.port.out.RenderedDocumentRepository;
import com.nanobaseai.actenora.template.domain.ContentHash;
import com.nanobaseai.actenora.template.domain.RenderFormat;
import com.nanobaseai.actenora.template.domain.RenderedDocument;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Optional;
import java.util.UUID;

public final class JdbcRenderedDocumentRepository implements RenderedDocumentRepository {

    private static final RowMapper<RenderedDocument> ROW_MAPPER = (rs, rowNum) -> RenderedDocument.builder()
            .id(RenderedDocumentId.of(rs.getObject("id", UUID.class)))
            .tenantId(TenantId.of(rs.getObject("tenant_id", UUID.class)))
            .renderJobId(RenderJobId.of(rs.getObject("render_job_id", UUID.class)))
            .noteId(rs.getObject("note_id", UUID.class))
            .templateVersionId(TemplateVersionId.of(rs.getObject("template_version_id", UUID.class)))
            .format(RenderFormat.valueOf(rs.getString("format")))
            .contentHash(new ContentHash(rs.getString("content_hash_sha256").toLowerCase()))
            .storageKey(rs.getString("storage_key"))
            .sizeBytes(rs.getLong("size_bytes"))
            .createdAt(JdbcInstant.get(rs, "created_at"))
            .build();

    private final JdbcTemplate jdbc;

    public JdbcRenderedDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public void save(RenderedDocument document) {
        String sql = """
                INSERT INTO template.rendered_document (
                    id, tenant_id, render_job_id, note_id, template_version_id, format,
                    content_hash_sha256, storage_key, size_bytes, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    storage_key = EXCLUDED.storage_key,
                    size_bytes = EXCLUDED.size_bytes
                """;
        jdbc.update(sql,
                document.id().value(),
                document.tenantId().value(),
                document.renderJobId().value(),
                document.noteId(),
                document.templateVersionId().value(),
                document.format().name(),
                document.contentHash().sha256Hex(),
                document.storageKey(),
                document.sizeBytes(),
                JdbcInstant.toTimestamp(document.createdAt())
        );
    }

    @Override
    public Optional<RenderedDocument> findById(TenantId tenantId, RenderedDocumentId id) {
        String sql = """
                SELECT id, tenant_id, render_job_id, note_id, template_version_id, format,
                       content_hash_sha256, storage_key, size_bytes, created_at
                FROM template.rendered_document
                WHERE tenant_id = ? AND id = ?
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId.value(), id.value()).stream().findFirst();
    }

    @Override
    public Optional<RenderedDocument> findByJobId(TenantId tenantId, RenderJobId jobId) {
        String sql = """
                SELECT id, tenant_id, render_job_id, note_id, template_version_id, format,
                       content_hash_sha256, storage_key, size_bytes, created_at
                FROM template.rendered_document
                WHERE tenant_id = ? AND render_job_id = ?
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId.value(), jobId.value()).stream().findFirst();
    }

    @Override
    public java.util.List<RenderedDocument> findByNoteId(TenantId tenantId, UUID noteId) {
        String sql = """
                SELECT id, tenant_id, render_job_id, note_id, template_version_id, format,
                       content_hash_sha256, storage_key, size_bytes, created_at
                FROM template.rendered_document
                WHERE tenant_id = ? AND note_id = ?
                ORDER BY created_at DESC
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId.value(), noteId);
    }
}
