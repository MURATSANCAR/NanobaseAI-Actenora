package com.nanobaseai.actenora.transcript.infrastructure.persistence;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import com.nanobaseai.actenora.transcript.api.TranscriptId;
import com.nanobaseai.actenora.transcript.application.port.out.TranscriptRepository;
import com.nanobaseai.actenora.transcript.domain.ContentHash;
import com.nanobaseai.actenora.transcript.domain.SourceFormat;
import com.nanobaseai.actenora.transcript.domain.Transcript;
import com.nanobaseai.actenora.transcript.domain.TranscriptDomainException;
import com.nanobaseai.actenora.transcript.domain.TranscriptSource;
import com.nanobaseai.actenora.transcript.domain.TranscriptStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Optional;
import java.util.UUID;

/** JDBC transcript store ({@code transcript.transcripts}). */
public final class JdbcTranscriptRepository implements TranscriptRepository {

    private static final String COLUMNS = """
            id, tenant_id, meeting_occurrence_id, source, external_transcript_id, language,
            source_format, raw_storage_key, normalized_storage_key, content_hash, status,
            fetched_at, normalized_at, created_at, updated_at, version
            """;

    private static final RowMapper<Transcript> ROW_MAPPER = (rs, rowNum) -> Transcript.builder()
            .id(TranscriptId.of(rs.getObject("id", UUID.class)))
            .tenantId(TenantId.of(rs.getObject("tenant_id", UUID.class)))
            .meetingOccurrenceId(rs.getObject("meeting_occurrence_id", UUID.class))
            .source(TranscriptSource.valueOf(rs.getString("source")))
            .externalTranscriptId(rs.getString("external_transcript_id"))
            .language(rs.getString("language"))
            .sourceFormat(SourceFormat.valueOf(rs.getString("source_format")))
            .rawStorageKey(rs.getString("raw_storage_key"))
            .normalizedStorageKey(rs.getString("normalized_storage_key"))
            .contentHash(new ContentHash(rs.getString("content_hash")))
            .status(TranscriptStatus.valueOf(rs.getString("status")))
            .fetchedAt(JdbcInstant.get(rs, "fetched_at"))
            .normalizedAt(JdbcInstant.get(rs, "normalized_at"))
            .createdAt(JdbcInstant.get(rs, "created_at"))
            .updatedAt(JdbcInstant.get(rs, "updated_at"))
            .version(rs.getLong("version"))
            .build();

    private final JdbcTemplate jdbc;

    public JdbcTranscriptRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    @Override
    public Transcript save(Transcript transcript) {
        // New aggregates start at version 0, but domain mutations (markPendingParse, etc.)
        // call touch() which bumps version before the first persist. Treat "row missing"
        // as insert so the first save after create+mutate still inserts instead of
        // optimistic-lock UPDATE against a non-existent row.
        if (findById(transcript.tenantId(), transcript.id()).isEmpty()) {
            insert(transcript);
            return transcript;
        }
        long previousVersion = transcript.version() - 1L;
        String sql = """
                UPDATE transcript.transcripts SET
                    normalized_storage_key = ?, status = ?, normalized_at = ?,
                    updated_at = ?, version = ?
                WHERE id = ? AND tenant_id = ? AND version = ?
                """;
        int updated = jdbc.update(sql,
                transcript.normalizedStorageKey().orElse(null),
                transcript.status().name(),
                transcript.normalizedAt().map(JdbcInstant::toTimestamp).orElse(null),
                JdbcInstant.toTimestamp(transcript.updatedAt()),
                transcript.version(),
                transcript.id().value(),
                transcript.tenantId().value(),
                previousVersion
        );
        if (updated != 1) {
            throw new TranscriptDomainException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "Transcript " + transcript.id().value() + " version conflict"
            );
        }
        return transcript;
    }

    @Override
    public Optional<Transcript> findById(TenantId tenantId, TranscriptId id) {
        String sql = "SELECT " + COLUMNS + " FROM transcript.transcripts WHERE tenant_id = ? AND id = ?";
        return jdbc.query(sql, ROW_MAPPER, tenantId.value(), id.value()).stream().findFirst();
    }

    @Override
    public Optional<Transcript> findByTenantAndContentHash(TenantId tenantId, ContentHash contentHash) {
        String sql = "SELECT " + COLUMNS + " FROM transcript.transcripts WHERE tenant_id = ? AND content_hash = ?";
        return jdbc.query(sql, ROW_MAPPER, tenantId.value(), contentHash.sha256Hex()).stream().findFirst();
    }

    @Override
    public Optional<Transcript> findByTenantAndExternalTranscriptId(TenantId tenantId, String externalTranscriptId) {
        String sql = "SELECT " + COLUMNS + """
                 FROM transcript.transcripts
                 WHERE tenant_id = ? AND external_transcript_id = ?
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId.value(), externalTranscriptId).stream().findFirst();
    }

    @Override
    public Optional<Transcript> findLatestByMeetingOccurrenceId(TenantId tenantId, UUID meetingOccurrenceId) {
        String sql = "SELECT " + COLUMNS + """
                 FROM transcript.transcripts
                 WHERE tenant_id = ? AND meeting_occurrence_id = ?
                 ORDER BY created_at DESC
                 LIMIT 1
                """;
        return jdbc.query(sql, ROW_MAPPER, tenantId.value(), meetingOccurrenceId).stream().findFirst();
    }

    private void insert(Transcript transcript) {
        String sql = """
                INSERT INTO transcript.transcripts (
                    id, tenant_id, meeting_occurrence_id, source, external_transcript_id, language,
                    source_format, raw_storage_key, normalized_storage_key, content_hash, status,
                    fetched_at, normalized_at, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbc.update(sql,
                transcript.id().value(),
                transcript.tenantId().value(),
                transcript.meetingOccurrenceId(),
                transcript.source().name(),
                transcript.externalTranscriptId().orElse(null),
                transcript.language().orElse(null),
                transcript.sourceFormat().name(),
                transcript.rawStorageKey(),
                transcript.normalizedStorageKey().orElse(null),
                transcript.contentHash().sha256Hex(),
                transcript.status().name(),
                JdbcInstant.toTimestamp(transcript.fetchedAt()),
                transcript.normalizedAt().map(JdbcInstant::toTimestamp).orElse(null),
                JdbcInstant.toTimestamp(transcript.createdAt()),
                JdbcInstant.toTimestamp(transcript.updatedAt()),
                transcript.version()
        );
    }
}
