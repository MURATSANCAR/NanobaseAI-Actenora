package com.nanobaseai.actenora.meeting.infrastructure.relation;

import com.nanobaseai.actenora.meeting.application.relation.port.MeetingRelationSuggestionRepository;
import com.nanobaseai.actenora.meeting.domain.relation.MeetingRelationSuggestion;
import com.nanobaseai.actenora.meeting.domain.relation.RelationType;
import com.nanobaseai.actenora.meeting.domain.relation.SuggestionStatus;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JdbcMeetingRelationSuggestionRepository implements MeetingRelationSuggestionRepository {

    private static final String COLUMNS = """
            id, tenant_id, source_occurrence_id, target_occurrence_id, proposed_type,
            confidence, reason, status, created_at, decided_at, decided_by
            """;

    private static final RowMapper<MeetingRelationSuggestion> MAPPER = (rs, rowNum) -> MeetingRelationSuggestion.rehydrate(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("source_occurrence_id", UUID.class),
            rs.getObject("target_occurrence_id", UUID.class),
            RelationType.valueOf(rs.getString("proposed_type")),
            rs.getBigDecimal("confidence"),
            rs.getString("reason"),
            SuggestionStatus.valueOf(rs.getString("status")),
            JdbcInstant.get(rs, "created_at"),
            JdbcInstant.get(rs, "decided_at"),
            rs.getString("decided_by")
    );

    private final JdbcTemplate jdbc;

    public JdbcMeetingRelationSuggestionRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public MeetingRelationSuggestion save(MeetingRelationSuggestion suggestion) {
        jdbc.update(
                """
                INSERT INTO meeting.meeting_relation_suggestions (
                    id, tenant_id, source_occurrence_id, target_occurrence_id, proposed_type,
                    confidence, reason, status, created_at, decided_at, decided_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    proposed_type = EXCLUDED.proposed_type,
                    confidence = EXCLUDED.confidence,
                    reason = EXCLUDED.reason,
                    status = EXCLUDED.status,
                    decided_at = EXCLUDED.decided_at,
                    decided_by = EXCLUDED.decided_by
                """,
                suggestion.id(),
                suggestion.tenantId(),
                suggestion.sourceOccurrenceId(),
                suggestion.targetOccurrenceId(),
                suggestion.proposedType().name(),
                suggestion.confidence(),
                suggestion.reason(),
                suggestion.status().name(),
                JdbcInstant.toTimestamp(suggestion.createdAt()),
                JdbcInstant.toTimestamp(suggestion.decidedAt()),
                suggestion.decidedBy()
        );
        return suggestion;
    }

    @Override
    public Optional<MeetingRelationSuggestion> findById(UUID tenantId, UUID suggestionId) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM meeting.meeting_relation_suggestions WHERE tenant_id = ? AND id = ?",
                MAPPER,
                tenantId,
                suggestionId
        ).stream().findFirst();
    }

    @Override
    public List<MeetingRelationSuggestion> findPendingByTenant(UUID tenantId) {
        return jdbc.query(
                """
                SELECT """ + COLUMNS + """
                 FROM meeting.meeting_relation_suggestions
                WHERE tenant_id = ? AND status = 'PENDING'
                """,
                MAPPER,
                tenantId
        );
    }
}
