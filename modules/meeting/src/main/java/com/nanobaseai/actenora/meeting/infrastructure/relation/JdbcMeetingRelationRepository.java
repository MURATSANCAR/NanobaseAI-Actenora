package com.nanobaseai.actenora.meeting.infrastructure.relation;

import com.nanobaseai.actenora.meeting.application.relation.port.MeetingRelationRepository;
import com.nanobaseai.actenora.meeting.domain.relation.MeetingRelation;
import com.nanobaseai.actenora.meeting.domain.relation.RelationType;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JdbcMeetingRelationRepository implements MeetingRelationRepository {

    private static final String COLUMNS = """
            id, tenant_id, source_occurrence_id, target_occurrence_id, relation_type,
            created_by, suggestion_id, created_at, updated_at, version
            """;

    private static final RowMapper<MeetingRelation> MAPPER = (rs, rowNum) -> MeetingRelation.rehydrate(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("source_occurrence_id", UUID.class),
            rs.getObject("target_occurrence_id", UUID.class),
            RelationType.valueOf(rs.getString("relation_type")),
            rs.getString("created_by"),
            rs.getObject("suggestion_id", UUID.class),
            JdbcInstant.get(rs, "created_at"),
            JdbcInstant.get(rs, "updated_at"),
            rs.getLong("version")
    );

    private final JdbcTemplate jdbc;

    public JdbcMeetingRelationRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public MeetingRelation save(MeetingRelation relation) {
        jdbc.update(
                """
                INSERT INTO meeting.meeting_relations (
                    id, tenant_id, source_occurrence_id, target_occurrence_id, relation_type,
                    created_by, suggestion_id, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    source_occurrence_id = EXCLUDED.source_occurrence_id,
                    target_occurrence_id = EXCLUDED.target_occurrence_id,
                    relation_type = EXCLUDED.relation_type,
                    created_by = EXCLUDED.created_by,
                    suggestion_id = EXCLUDED.suggestion_id,
                    updated_at = EXCLUDED.updated_at,
                    version = EXCLUDED.version
                """,
                relation.id(),
                relation.tenantId(),
                relation.sourceOccurrenceId(),
                relation.targetOccurrenceId(),
                relation.relationType().name(),
                relation.createdBy(),
                relation.suggestionId(),
                JdbcInstant.toTimestamp(relation.createdAt()),
                JdbcInstant.toTimestamp(relation.updatedAt()),
                relation.version()
        );
        return relation;
    }

    @Override
    public Optional<MeetingRelation> findById(UUID tenantId, UUID relationId) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM meeting.meeting_relations WHERE tenant_id = ? AND id = ?",
                MAPPER,
                tenantId,
                relationId
        ).stream().findFirst();
    }

    @Override
    public List<MeetingRelation> findAllByTenant(UUID tenantId) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM meeting.meeting_relations WHERE tenant_id = ?",
                MAPPER,
                tenantId
        );
    }

    @Override
    public List<MeetingRelation> findByOccurrence(UUID tenantId, UUID occurrenceId) {
        return jdbc.query(
                """
                SELECT """ + COLUMNS + """
                 FROM meeting.meeting_relations
                WHERE tenant_id = ?
                  AND (source_occurrence_id = ? OR target_occurrence_id = ?)
                """,
                MAPPER,
                tenantId,
                occurrenceId,
                occurrenceId
        );
    }
}
