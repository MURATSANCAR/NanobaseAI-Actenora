package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.OpenQuestionRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.model.OpenQuestion;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcOpenQuestionRepository implements OpenQuestionRepository {

    private static final RowMapper<OpenQuestion> ROW_MAPPER = (rs, rowNum) -> OpenQuestion.rehydrate(
            rs.getObject("id", UUID.class),
            TenantId.of(rs.getObject("tenant_id", UUID.class)),
            rs.getObject("note_id", UUID.class),
            rs.getObject("note_version_id", UUID.class),
            rs.getString("text"),
            rs.getBoolean("requires_manual_review"),
            (Double) rs.getObject("ai_confidence"),
            JdbcInstant.get(rs, "created_at"),
            JdbcInstant.get(rs, "updated_at"),
            rs.getLong("version")
    );

    private final JdbcTemplate jdbc;

    public JdbcOpenQuestionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public OpenQuestion save(OpenQuestion question) {
        String sql = """
                INSERT INTO meetingintelligence.open_questions (
                    id, tenant_id, note_id, note_version_id, text,
                    requires_manual_review, ai_confidence, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    text = EXCLUDED.text,
                    requires_manual_review = EXCLUDED.requires_manual_review,
                    updated_at = EXCLUDED.updated_at,
                    version = EXCLUDED.version
                """;
        jdbc.update(sql,
                question.id(),
                question.tenantId().value(),
                question.noteId(),
                question.noteVersionId(),
                question.text(),
                question.requiresManualReview(),
                question.aiConfidence(),
                JdbcInstant.toTimestamp(question.createdAt()),
                JdbcInstant.toTimestamp(question.updatedAt()),
                question.version()
        );
        return question;
    }

    @Override
    public Optional<OpenQuestion> findByIdAndTenantId(UUID id, TenantId tenantId) {
        String sql = """
                SELECT id, tenant_id, note_id, note_version_id, text,
                       requires_manual_review, ai_confidence, created_at, updated_at, version
                FROM meetingintelligence.open_questions
                WHERE id = ? AND tenant_id = ?
                """;
        return jdbc.query(sql, ROW_MAPPER, id, tenantId.value()).stream().findFirst();
    }

    @Override
    public List<OpenQuestion> findByNoteId(UUID noteId, TenantId tenantId) {
        String sql = """
                SELECT id, tenant_id, note_id, note_version_id, text,
                       requires_manual_review, ai_confidence, created_at, updated_at, version
                FROM meetingintelligence.open_questions
                WHERE note_id = ? AND tenant_id = ?
                ORDER BY created_at
                """;
        return jdbc.query(sql, ROW_MAPPER, noteId, tenantId.value());
    }
}
