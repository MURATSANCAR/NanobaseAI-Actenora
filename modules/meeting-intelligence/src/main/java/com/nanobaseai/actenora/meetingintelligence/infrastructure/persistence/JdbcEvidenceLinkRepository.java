package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.EvidenceLinkRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.model.EvidenceLink;
import com.nanobaseai.actenora.meetingintelligence.domain.model.EvidenceSubjectType;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcEvidenceLinkRepository implements EvidenceLinkRepository {

    private static final RowMapper<EvidenceLink> ROW_MAPPER = (rs, rowNum) -> EvidenceLink.rehydrate(
            rs.getObject("id", UUID.class),
            TenantId.of(rs.getObject("tenant_id", UUID.class)),
            rs.getObject("note_id", UUID.class),
            rs.getObject("note_version_id", UUID.class),
            EvidenceSubjectType.valueOf(rs.getString("subject_type")),
            rs.getObject("subject_id", UUID.class),
            rs.getString("evidence_segment_id"),
            JdbcInstant.get(rs, "created_at")
    );

    private final JdbcTemplate jdbc;

    public JdbcEvidenceLinkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public EvidenceLink save(EvidenceLink link) {
        String sql = """
                INSERT INTO meetingintelligence.evidence_links (
                    id, tenant_id, note_id, note_version_id, subject_type, subject_id,
                    evidence_segment_id, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """;
        jdbc.update(sql,
                link.id(),
                link.tenantId().value(),
                link.noteId(),
                link.noteVersionId(),
                link.subjectType().name(),
                link.subjectId(),
                link.evidenceSegmentId(),
                JdbcInstant.toTimestamp(link.createdAt())
        );
        return link;
    }

    @Override
    public Optional<EvidenceLink> findByIdAndTenantId(UUID id, TenantId tenantId) {
        String sql = """
                SELECT id, tenant_id, note_id, note_version_id, subject_type, subject_id,
                       evidence_segment_id, created_at
                FROM meetingintelligence.evidence_links
                WHERE id = ? AND tenant_id = ?
                """;
        return jdbc.query(sql, ROW_MAPPER, id, tenantId.value()).stream().findFirst();
    }

    @Override
    public List<EvidenceLink> findByNoteId(UUID noteId, TenantId tenantId) {
        String sql = """
                SELECT id, tenant_id, note_id, note_version_id, subject_type, subject_id,
                       evidence_segment_id, created_at
                FROM meetingintelligence.evidence_links
                WHERE note_id = ? AND tenant_id = ?
                ORDER BY created_at
                """;
        return jdbc.query(sql, ROW_MAPPER, noteId, tenantId.value());
    }

    @Override
    public List<EvidenceLink> findBySubjectId(UUID subjectId, TenantId tenantId) {
        String sql = """
                SELECT id, tenant_id, note_id, note_version_id, subject_type, subject_id,
                       evidence_segment_id, created_at
                FROM meetingintelligence.evidence_links
                WHERE subject_id = ? AND tenant_id = ?
                ORDER BY created_at
                """;
        return jdbc.query(sql, ROW_MAPPER, subjectId, tenantId.value());
    }
}
