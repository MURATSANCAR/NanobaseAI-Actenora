package com.nanobaseai.actenora.transcript.infrastructure.persistence;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.api.TranscriptId;
import com.nanobaseai.actenora.transcript.application.port.out.TranscriptSegmentRepository;
import com.nanobaseai.actenora.transcript.domain.ContentHash;
import com.nanobaseai.actenora.transcript.domain.TranscriptSegment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

/** JDBC transcript segment store ({@code transcript.transcript_segments}). */
public final class JdbcTranscriptSegmentRepository implements TranscriptSegmentRepository {

    private static final RowMapper<TranscriptSegment> ROW_MAPPER = (rs, rowNum) -> new TranscriptSegment(
            rs.getObject("id", UUID.class),
            TenantId.of(rs.getObject("tenant_id", UUID.class)),
            TranscriptId.of(rs.getObject("transcript_id", UUID.class)),
            rs.getInt("sequence"),
            rs.getString("speaker_id"),
            rs.getString("speaker_display_name"),
            rs.getLong("start_offset_ms"),
            rs.getLong("end_offset_ms"),
            rs.getString("content")
    );

    private final JdbcTemplate jdbc;

    public JdbcTranscriptSegmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public void replaceAll(TenantId tenantId, TranscriptId transcriptId, List<TranscriptSegment> segments) {
        jdbc.update(
                "DELETE FROM transcript.transcript_segments WHERE tenant_id = ? AND transcript_id = ?",
                tenantId.value(),
                transcriptId.value()
        );
        if (segments.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO transcript.transcript_segments (
                    id, tenant_id, transcript_id, sequence, speaker_id, speaker_display_name,
                    start_offset_ms, end_offset_ms, content, content_hash
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbc.batchUpdate(sql, segments, segments.size(), (ps, segment) -> {
            ps.setObject(1, segment.id());
            ps.setObject(2, tenantId.value());
            ps.setObject(3, transcriptId.value());
            ps.setInt(4, segment.sequence());
            ps.setString(5, segment.speakerId().orElse(null));
            ps.setString(6, segment.speakerDisplayName().orElse(null));
            ps.setLong(7, segment.startOffsetMs());
            ps.setLong(8, segment.endOffsetMs());
            ps.setString(9, segment.content());
            ps.setString(10, segment.contentHash().sha256Hex());
        });
    }

    @Override
    public List<TranscriptSegment> findByTranscript(TenantId tenantId, TranscriptId transcriptId) {
        String sql = """
                SELECT id, tenant_id, transcript_id, sequence, speaker_id, speaker_display_name,
                       start_offset_ms, end_offset_ms, content
                FROM transcript.transcript_segments
                WHERE tenant_id = ? AND transcript_id = ?
                ORDER BY sequence ASC
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId.value(), transcriptId.value());
    }

    @Override
    public List<TranscriptSegment> searchByTranscript(
            TenantId tenantId,
            TranscriptId transcriptId,
            String query,
            int limit
    ) {
        String sql = """
                SELECT id, tenant_id, transcript_id, sequence, speaker_id, speaker_display_name,
                       start_offset_ms, end_offset_ms, content
                FROM transcript.transcript_segments
                CROSS JOIN LATERAL (
                    SELECT count(*) AS matched_terms
                    FROM unnest(regexp_split_to_array(trim(?), '\\s+')) AS terms(term)
                    WHERE term <> ''
                      AND content_tsv @@ plainto_tsquery('simple', term)
                ) relevance
                WHERE tenant_id = ?
                  AND transcript_id = ?
                  AND relevance.matched_terms > 0
                ORDER BY relevance.matched_terms DESC,
                         sequence ASC
                LIMIT ?
                """;
        return jdbc.query(
                sql,
                ROW_MAPPER,
                query,
                tenantId.value(),
                transcriptId.value(),
                limit
        );
    }

    @Override
    public void deleteByTranscript(TenantId tenantId, TranscriptId transcriptId) {
        jdbc.update(
                "DELETE FROM transcript.transcript_segments WHERE tenant_id = ? AND transcript_id = ?",
                tenantId.value(),
                transcriptId.value()
        );
    }
}
