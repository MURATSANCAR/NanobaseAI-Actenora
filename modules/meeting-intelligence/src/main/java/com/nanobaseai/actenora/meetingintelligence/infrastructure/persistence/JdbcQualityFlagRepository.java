package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.QualityFlagRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.model.EvidenceSubjectType;
import com.nanobaseai.actenora.meetingintelligence.domain.model.QualityFlag;
import com.nanobaseai.actenora.meetingintelligence.domain.model.QualityFlagCode;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

public final class JdbcQualityFlagRepository implements QualityFlagRepository {

    private static final RowMapper<QualityFlag> ROW_MAPPER = (rs, rowNum) -> {
        String subjectType = rs.getString("subject_type");
        return QualityFlag.rehydrate(
                rs.getObject("id", UUID.class),
                TenantId.of(rs.getObject("tenant_id", UUID.class)),
                rs.getObject("note_id", UUID.class),
                rs.getObject("note_version_id", UUID.class),
                QualityFlagCode.valueOf(rs.getString("code")),
                rs.getString("detail"),
                subjectType == null || subjectType.isBlank() ? null : EvidenceSubjectType.valueOf(subjectType),
                rs.getObject("subject_id", UUID.class),
                JdbcInstant.get(rs, "created_at")
        );
    };

    private final JdbcTemplate jdbc;

    public JdbcQualityFlagRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public QualityFlag save(QualityFlag flag) {
        String sql = """
                INSERT INTO meetingintelligence.quality_flags (
                    id, tenant_id, note_id, note_version_id, code, detail,
                    subject_type, subject_id, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """;
        jdbc.update(sql,
                flag.id(),
                flag.tenantId().value(),
                flag.noteId(),
                flag.noteVersionId(),
                flag.code().name(),
                flag.detail(),
                flag.subjectType() == null ? null : flag.subjectType().name(),
                flag.subjectId(),
                JdbcInstant.toTimestamp(flag.createdAt())
        );
        return flag;
    }

    @Override
    public List<QualityFlag> findByNoteId(UUID noteId, TenantId tenantId) {
        String sql = """
                SELECT id, tenant_id, note_id, note_version_id, code, detail,
                       subject_type, subject_id, created_at
                FROM meetingintelligence.quality_flags
                WHERE note_id = ? AND tenant_id = ?
                ORDER BY created_at
                """;
        return jdbc.query(sql, ROW_MAPPER, noteId, tenantId.value());
    }
}
