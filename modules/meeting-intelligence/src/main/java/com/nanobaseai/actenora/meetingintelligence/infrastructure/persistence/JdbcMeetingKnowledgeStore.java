package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingKnowledgeStorePort;
import com.nanobaseai.actenora.meetingintelligence.domain.knowledge.KnowledgeItemKind;
import com.nanobaseai.actenora.meetingintelligence.domain.knowledge.KnowledgeSearchHit;
import com.nanobaseai.actenora.meetingintelligence.domain.knowledge.MeetingKnowledgeItem;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;

public final class JdbcMeetingKnowledgeStore implements MeetingKnowledgeStorePort {

    private final JdbcTemplate jdbc;

    public JdbcMeetingKnowledgeStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void upsert(MeetingKnowledgeItem item) {
        Objects.requireNonNull(item, "item");
        String embeddingLiteral = item.embedding().map(JdbcMeetingKnowledgeStore::toVectorLiteral).orElse(null);
        jdbc.update(
                """
                INSERT INTO meetingintelligence.meeting_knowledge_item (
                    id, tenant_id, meeting_occurrence_id, note_id, note_version_id,
                    source_item_id, item_kind, content, embedding, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector), ?)
                ON CONFLICT (tenant_id, source_item_id, item_kind) DO UPDATE SET
                    id = EXCLUDED.id,
                    meeting_occurrence_id = EXCLUDED.meeting_occurrence_id,
                    note_id = EXCLUDED.note_id,
                    note_version_id = EXCLUDED.note_version_id,
                    content = EXCLUDED.content,
                    embedding = EXCLUDED.embedding,
                    created_at = EXCLUDED.created_at
                """,
                item.id(),
                item.tenantId().value(),
                item.meetingOccurrenceId(),
                item.noteId(),
                item.noteVersionId(),
                item.sourceItemId(),
                item.itemKind().name(),
                item.content(),
                embeddingLiteral,
                JdbcInstant.toTimestamp(item.createdAt())
        );
    }

    @Override
    public List<KnowledgeSearchHit> searchFts(TenantId tenantId, String query, int limit) {
        return jdbc.query(
                """
                SELECT id, tenant_id, meeting_occurrence_id, source_item_id, item_kind, content,
                       ts_rank(content_tsv, plainto_tsquery('simple', ?)) AS score
                FROM meetingintelligence.meeting_knowledge_item
                WHERE tenant_id = ?
                  AND content_tsv @@ plainto_tsquery('simple', ?)
                ORDER BY score DESC
                LIMIT ?
                """,
                HIT_MAPPER,
                query,
                tenantId.value(),
                query,
                limit
        );
    }

    @Override
    public List<KnowledgeSearchHit> searchVector(TenantId tenantId, float[] embedding, int limit) {
        String literal = toVectorLiteral(embedding);
        return jdbc.query(
                """
                SELECT id, tenant_id, meeting_occurrence_id, source_item_id, item_kind, content,
                       (1 - (embedding <=> CAST(? AS vector))) AS score
                FROM meetingintelligence.meeting_knowledge_item
                WHERE tenant_id = ?
                  AND embedding IS NOT NULL
                ORDER BY embedding <=> CAST(? AS vector)
                LIMIT ?
                """,
                HIT_MAPPER,
                literal,
                tenantId.value(),
                literal,
                limit
        );
    }

    @Override
    public List<MeetingKnowledgeItem> findByOccurrence(TenantId tenantId, UUID meetingOccurrenceId) {
        return jdbc.query(
                """
                SELECT id, tenant_id, meeting_occurrence_id, note_id, note_version_id,
                       source_item_id, item_kind, content, embedding, created_at
                FROM meetingintelligence.meeting_knowledge_item
                WHERE tenant_id = ? AND meeting_occurrence_id = ?
                """,
                ITEM_MAPPER,
                tenantId.value(),
                meetingOccurrenceId
        );
    }

    @Override
    public List<MeetingKnowledgeItem> findBySource(
            TenantId tenantId,
            UUID sourceItemId,
            KnowledgeItemKind kind
    ) {
        return jdbc.query(
                """
                SELECT id, tenant_id, meeting_occurrence_id, note_id, note_version_id,
                       source_item_id, item_kind, content, embedding, created_at
                FROM meetingintelligence.meeting_knowledge_item
                WHERE tenant_id = ? AND source_item_id = ? AND item_kind = ?
                """,
                ITEM_MAPPER,
                tenantId.value(),
                sourceItemId,
                kind.name()
        );
    }

    private static final RowMapper<KnowledgeSearchHit> HIT_MAPPER = (rs, rowNum) -> new KnowledgeSearchHit(
            rs.getObject("id", UUID.class),
            TenantId.of(rs.getObject("tenant_id", UUID.class)),
            rs.getObject("meeting_occurrence_id", UUID.class),
            rs.getObject("source_item_id", UUID.class),
            KnowledgeItemKind.valueOf(rs.getString("item_kind")),
            rs.getString("content"),
            rs.getDouble("score")
    );

    private static final RowMapper<MeetingKnowledgeItem> ITEM_MAPPER = (rs, rowNum) -> new MeetingKnowledgeItem(
            rs.getObject("id", UUID.class),
            TenantId.of(rs.getObject("tenant_id", UUID.class)),
            rs.getObject("meeting_occurrence_id", UUID.class),
            rs.getObject("note_id", UUID.class),
            rs.getObject("note_version_id", UUID.class),
            rs.getObject("source_item_id", UUID.class),
            KnowledgeItemKind.valueOf(rs.getString("item_kind")),
            rs.getString("content"),
            readEmbedding(rs),
            JdbcInstant.get(rs, "created_at")
    );

    private static Optional<float[]> readEmbedding(ResultSet rs) throws SQLException {
        Object raw = rs.getObject("embedding");
        if (raw == null) {
            return Optional.empty();
        }
        if (raw instanceof Array array) {
            Object[] values = (Object[]) array.getArray();
            float[] embedding = new float[values.length];
            for (int i = 0; i < values.length; i++) {
                embedding[i] = ((Number) values[i]).floatValue();
            }
            return Optional.of(embedding);
        }
        String text = raw.toString().trim();
        if (text.startsWith("[") && text.endsWith("]")) {
            text = text.substring(1, text.length() - 1);
        }
        if (text.isBlank()) {
            return Optional.empty();
        }
        String[] parts = text.split(",");
        float[] embedding = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            embedding[i] = Float.parseFloat(parts[i].trim());
        }
        return Optional.of(embedding);
    }

    static String toVectorLiteral(float[] embedding) {
        Objects.requireNonNull(embedding, "embedding");
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : embedding) {
            joiner.add(Float.toString(value));
        }
        return joiner.toString();
    }
}
