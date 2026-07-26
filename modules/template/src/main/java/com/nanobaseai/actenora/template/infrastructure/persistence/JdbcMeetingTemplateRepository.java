package com.nanobaseai.actenora.template.infrastructure.persistence;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcJson;
import com.nanobaseai.actenora.template.api.MeetingTemplateId;
import com.nanobaseai.actenora.template.api.TemplateVersionId;
import com.nanobaseai.actenora.template.application.port.out.MeetingTemplateRepository;
import com.nanobaseai.actenora.template.domain.ContentSchema;
import com.nanobaseai.actenora.template.domain.DesignComponent;
import com.nanobaseai.actenora.template.domain.DesignSchema;
import com.nanobaseai.actenora.template.domain.MeetingTemplate;
import com.nanobaseai.actenora.template.domain.TemplateComponentType;
import com.nanobaseai.actenora.template.domain.TemplateVersion;
import com.nanobaseai.actenora.template.domain.TemplateVersionStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** JDBC meeting template store ({@code template.meeting_template}, {@code template.template_version}). */
public final class JdbcMeetingTemplateRepository implements MeetingTemplateRepository {

    private final JdbcTemplate jdbc;

    public JdbcMeetingTemplateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public void save(MeetingTemplate template) {
        jdbc.execute((Connection conn) -> {
            conn.setAutoCommit(false);
            try {
                upsertTemplate(conn, template);
                for (TemplateVersion version : template.versions()) {
                    upsertVersion(conn, version, template.publishedVersionId().orElse(null));
                }
                conn.commit();
                return null;
            } catch (RuntimeException ex) {
                conn.rollback();
                throw ex;
            } catch (SQLException ex) {
                conn.rollback();
                throw new IllegalStateException("Failed to save template " + template.id().value(), ex);
            }
        });
    }

    @Override
    public Optional<MeetingTemplate> findById(TenantId tenantId, MeetingTemplateId id) {
        String sql = """
                SELECT id, tenant_id, name, published_version_id, created_at, updated_at
                FROM template.meeting_template WHERE tenant_id = ? AND id = ?
                """;
        List<MeetingTemplate> templates = jdbc.query(sql, (rs, rowNum) -> {
            UUID published = rs.getObject("published_version_id", UUID.class);
            return MeetingTemplate.builder()
                    .id(MeetingTemplateId.of(rs.getObject("id", UUID.class)))
                    .tenantId(TenantId.of(rs.getObject("tenant_id", UUID.class)))
                    .name(rs.getString("name"))
                    .publishedVersionId(published == null ? null : TemplateVersionId.of(published))
                    .createdAt(JdbcInstant.get(rs, "created_at"))
                    .updatedAt(JdbcInstant.get(rs, "updated_at"))
                    .build();
        }, tenantId.value(), id.value());
        return templates.stream().findFirst().map(template -> {
            List<TemplateVersion> versions = loadVersions(tenantId, id);
            return MeetingTemplate.builder()
                    .id(template.id())
                    .tenantId(template.tenantId())
                    .name(template.name())
                    .publishedVersionId(template.publishedVersionId().orElse(null))
                    .versions(versions)
                    .createdAt(template.createdAt())
                    .updatedAt(template.updatedAt())
                    .build();
        });
    }

    @Override
    public Optional<TemplateVersion> findVersion(TenantId tenantId, TemplateVersionId versionId) {
        String sql = """
                SELECT id, template_id, tenant_id, version_number, status, design_schema_json,
                       content_schema_json, changelog, created_at, published_at, updated_at
                FROM template.template_version WHERE tenant_id = ? AND id = ?
                """;
        return jdbc.query(sql, (rs, rowNum) -> mapVersion(rs), tenantId.value(), versionId.value())
                .stream()
                .findFirst();
    }

    @Override
    public void saveVersion(MeetingTemplate template, TemplateVersion version) {
        save(template);
    }

    private List<TemplateVersion> loadVersions(TenantId tenantId, MeetingTemplateId templateId) {
        String sql = """
                SELECT id, template_id, tenant_id, version_number, status, design_schema_json,
                       content_schema_json, changelog, created_at, published_at, updated_at
                FROM template.template_version
                WHERE tenant_id = ? AND template_id = ?
                ORDER BY version_number
                """;
        return jdbc.query(sql, (rs, rowNum) -> mapVersion(rs), tenantId.value(), templateId.value());
    }

    private TemplateVersion mapVersion(java.sql.ResultSet rs) throws java.sql.SQLException {
        DesignSchemaDto designDto = JdbcJson.read(rs.getString("design_schema_json"), DesignSchemaDto.class);
        ContentSchemaDto contentDto = JdbcJson.read(rs.getString("content_schema_json"), ContentSchemaDto.class);
        DesignSchema design = designDto == null ? null : new DesignSchema(
                designDto.schemaVersion(),
                designDto.pageSize(),
                designDto.components().stream()
                        .map(c -> new DesignComponent(
                                c.id(), TemplateComponentType.valueOf(c.type()), c.order(), c.props()
                        ))
                        .toList()
        );
        ContentSchema content = contentDto == null
                ? ContentSchema.empty()
                : new ContentSchema(contentDto.schemaVersion(), contentDto.bindings());
        return TemplateVersion.builder()
                .id(TemplateVersionId.of(rs.getObject("id", UUID.class)))
                .templateId(MeetingTemplateId.of(rs.getObject("template_id", UUID.class)))
                .tenantId(TenantId.of(rs.getObject("tenant_id", UUID.class)))
                .versionNumber(rs.getInt("version_number"))
                .status(TemplateVersionStatus.valueOf(rs.getString("status")))
                .designSchema(design)
                .contentSchema(content)
                .changelog(rs.getString("changelog"))
                .createdAt(JdbcInstant.get(rs, "created_at"))
                .publishedAt(JdbcInstant.get(rs, "published_at"))
                .updatedAt(JdbcInstant.get(rs, "updated_at"))
                .build();
    }

    private void upsertTemplate(Connection conn, MeetingTemplate template) throws SQLException {
        String sql = """
                INSERT INTO template.meeting_template (
                    id, tenant_id, name, published_version_id, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    name = EXCLUDED.name,
                    published_version_id = EXCLUDED.published_version_id,
                    updated_at = EXCLUDED.updated_at
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, template.id().value());
            ps.setObject(2, template.tenantId().value());
            ps.setString(3, template.name());
            ps.setObject(4, template.publishedVersionId().map(TemplateVersionId::value).orElse(null));
            ps.setTimestamp(5, JdbcInstant.toTimestamp(template.createdAt()));
            ps.setTimestamp(6, JdbcInstant.toTimestamp(template.updatedAt()));
            ps.executeUpdate();
        }
    }

    private void upsertVersion(Connection conn, TemplateVersion version, TemplateVersionId publishedId) throws SQLException {
        String sql = """
                INSERT INTO template.template_version (
                    id, template_id, tenant_id, version_number, status, design_schema_json,
                    content_schema_json, changelog, created_at, published_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status,
                    design_schema_json = EXCLUDED.design_schema_json,
                    content_schema_json = EXCLUDED.content_schema_json,
                    changelog = EXCLUDED.changelog,
                    published_at = EXCLUDED.published_at,
                    updated_at = EXCLUDED.updated_at
                """;
        DesignSchema design = version.designSchema().orElse(null);
        DesignSchemaDto designDto = design == null ? null : new DesignSchemaDto(
                design.schemaVersion(),
                design.pageSize(),
                design.components().stream()
                        .map(c -> new DesignComponentDto(c.id(), c.type().name(), c.order(), c.props()))
                        .toList()
        );
        ContentSchema content = version.contentSchema();
        ContentSchemaDto contentDto = new ContentSchemaDto(content.schemaVersion(), content.bindings());
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, version.id().value());
            ps.setObject(2, version.templateId().value());
            ps.setObject(3, version.tenantId().value());
            ps.setInt(4, version.versionNumber());
            ps.setString(5, version.status().name());
            ps.setString(6, designDto == null ? null : JdbcJson.write(designDto));
            ps.setString(7, JdbcJson.write(contentDto));
            ps.setString(8, version.changelog());
            ps.setTimestamp(9, JdbcInstant.toTimestamp(version.createdAt()));
            ps.setTimestamp(10, version.publishedAt().map(JdbcInstant::toTimestamp).orElse(null));
            ps.setTimestamp(11, JdbcInstant.toTimestamp(version.updatedAt()));
            ps.executeUpdate();
        }
    }

    private record DesignSchemaDto(int schemaVersion, String pageSize, List<DesignComponentDto> components) {
    }

    private record DesignComponentDto(UUID id, String type, int order, Map<String, String> props) {
    }

    private record ContentSchemaDto(int schemaVersion, Map<String, String> bindings) {
    }
}
