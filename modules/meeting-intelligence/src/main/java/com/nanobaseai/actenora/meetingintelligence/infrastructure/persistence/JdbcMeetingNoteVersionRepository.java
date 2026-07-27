package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingNoteVersionRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNoteStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNoteVersion;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ModelPromptSchemaProvenance;
import com.nanobaseai.actenora.meetingintelligence.domain.model.NoteVersionSource;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC meeting note version store ({@code meetingintelligence.meeting_note_versions}). */
public final class JdbcMeetingNoteVersionRepository implements MeetingNoteVersionRepository {

    private static final RowMapper<MeetingNoteVersion> ROW_MAPPER = (rs, rowNum) -> {
        ModelPromptSchemaProvenance provenance = null;
        String modelId = rs.getString("model_id");
        if (modelId != null) {
            double confidence = rs.getDouble("ai_confidence");
            if (rs.wasNull()) {
                confidence = 0.0d;
            }
            provenance = ModelPromptSchemaProvenance.of(
                    modelId,
                    rs.getString("prompt_version_id"),
                    rs.getString("schema_id"),
                    confidence
            );
        }
        String approvalStatus = rs.getString("approval_status");
        return MeetingNoteVersion.rehydrate(
                rs.getObject("id", UUID.class),
                TenantId.of(rs.getObject("tenant_id", UUID.class)),
                rs.getObject("note_id", UUID.class),
                rs.getInt("version_number"),
                rs.getString("executive_summary"),
                NoteVersionSource.valueOf(rs.getString("source")),
                provenance,
                rs.getString("correction_reason"),
                rs.getObject("created_by_user_id", UUID.class),
                JdbcInstant.get(rs, "created_at"),
                approvalStatus == null
                        ? MeetingNoteStatus.DRAFT
                        : MeetingNoteStatus.valueOf(approvalStatus)
        );
    };

    private final JdbcTemplate jdbc;

    public JdbcMeetingNoteVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public MeetingNoteVersion save(MeetingNoteVersion version) {
        String sql = """
                INSERT INTO meetingintelligence.meeting_note_versions (
                    id, tenant_id, note_id, version_number, executive_summary, source,
                    model_id, prompt_version_id, schema_id, ai_confidence, correction_reason,
                    created_by_user_id, created_at, approval_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, note_id, version_number) DO UPDATE SET
                    executive_summary = EXCLUDED.executive_summary,
                    correction_reason = EXCLUDED.correction_reason,
                    approval_status = EXCLUDED.approval_status
                """;
        ModelPromptSchemaProvenance provenance = version.provenance();
        jdbc.update(sql,
                version.id(),
                version.tenantId().value(),
                version.noteId(),
                version.versionNumber(),
                version.executiveSummary(),
                version.source().name(),
                provenance == null ? null : provenance.modelId(),
                provenance == null ? null : provenance.promptVersionId(),
                provenance == null ? null : provenance.schemaId(),
                provenance == null ? null : provenance.aiConfidence(),
                version.correctionReason(),
                version.createdByUserId(),
                JdbcInstant.toTimestamp(version.createdAt()),
                version.approvalStatus().name()
        );
        return version;
    }

    @Override
    public Optional<MeetingNoteVersion> findByIdAndTenantId(UUID id, TenantId tenantId) {
        String sql = """
                SELECT id, tenant_id, note_id, version_number, executive_summary, source,
                       model_id, prompt_version_id, schema_id, ai_confidence, correction_reason,
                       created_by_user_id, created_at, approval_status
                FROM meetingintelligence.meeting_note_versions
                WHERE id = ? AND tenant_id = ?
                """;
        return jdbc.query(sql, ROW_MAPPER, id, tenantId.value()).stream().findFirst();
    }

    @Override
    public Optional<MeetingNoteVersion> findByNoteIdAndVersionNumber(UUID noteId, int versionNumber, TenantId tenantId) {
        String sql = """
                SELECT id, tenant_id, note_id, version_number, executive_summary, source,
                       model_id, prompt_version_id, schema_id, ai_confidence, correction_reason,
                       created_by_user_id, created_at, approval_status
                FROM meetingintelligence.meeting_note_versions
                WHERE note_id = ? AND version_number = ? AND tenant_id = ?
                """;
        return jdbc.query(sql, ROW_MAPPER, noteId, versionNumber, tenantId.value()).stream().findFirst();
    }

    @Override
    public List<MeetingNoteVersion> findAllByNoteId(UUID noteId, TenantId tenantId) {
        String sql = """
                SELECT id, tenant_id, note_id, version_number, executive_summary, source,
                       model_id, prompt_version_id, schema_id, ai_confidence, correction_reason,
                       created_by_user_id, created_at, approval_status
                FROM meetingintelligence.meeting_note_versions
                WHERE note_id = ? AND tenant_id = ?
                """;
        return jdbc.query(sql, ROW_MAPPER, noteId, tenantId.value()).stream()
                .sorted(Comparator.comparingInt(MeetingNoteVersion::versionNumber))
                .toList();
    }
}
