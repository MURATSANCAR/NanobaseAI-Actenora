package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.ArtifactMetadataStorePort;
import com.nanobaseai.actenora.meetingintelligence.domain.artifact.ArtifactKind;
import com.nanobaseai.actenora.meetingintelligence.domain.artifact.ArtifactMetadata;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JdbcArtifactMetadataStore implements ArtifactMetadataStorePort {

    private final JdbcTemplate jdbc;

    public JdbcArtifactMetadataStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public ArtifactMetadata save(ArtifactMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        jdbc.update(
                """
                INSERT INTO meetingintelligence.artifact_metadata (
                    id, tenant_id, meeting_occurrence_id, note_id, note_version_id,
                    artifact_kind, storage_key, content_type, content_length_bytes,
                    checksum_sha256, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, storage_key) DO UPDATE SET
                    id = EXCLUDED.id,
                    meeting_occurrence_id = EXCLUDED.meeting_occurrence_id,
                    note_id = EXCLUDED.note_id,
                    note_version_id = EXCLUDED.note_version_id,
                    artifact_kind = EXCLUDED.artifact_kind,
                    content_type = EXCLUDED.content_type,
                    content_length_bytes = EXCLUDED.content_length_bytes,
                    checksum_sha256 = EXCLUDED.checksum_sha256,
                    created_at = EXCLUDED.created_at
                """,
                metadata.id(),
                metadata.tenantId().value(),
                metadata.meetingOccurrenceId().orElse(null),
                metadata.noteId().orElse(null),
                metadata.noteVersionId().orElse(null),
                metadata.artifactKind().name(),
                metadata.storageKey(),
                metadata.contentType(),
                metadata.contentLengthBytes().orElse(null),
                metadata.checksumSha256().orElse(null),
                JdbcInstant.toTimestamp(metadata.createdAt())
        );
        return metadata;
    }

    @Override
    public Optional<ArtifactMetadata> findByKey(TenantId tenantId, String storageKey) {
        List<ArtifactMetadata> rows = jdbc.query(
                """
                SELECT id, tenant_id, meeting_occurrence_id, note_id, note_version_id,
                       artifact_kind, storage_key, content_type, content_length_bytes,
                       checksum_sha256, created_at
                FROM meetingintelligence.artifact_metadata
                WHERE tenant_id = ? AND storage_key = ?
                """,
                MAPPER,
                tenantId.value(),
                storageKey
        );
        return rows.stream().findFirst();
    }

    @Override
    public List<ArtifactMetadata> findByOccurrence(TenantId tenantId, UUID meetingOccurrenceId) {
        return jdbc.query(
                """
                SELECT id, tenant_id, meeting_occurrence_id, note_id, note_version_id,
                       artifact_kind, storage_key, content_type, content_length_bytes,
                       checksum_sha256, created_at
                FROM meetingintelligence.artifact_metadata
                WHERE tenant_id = ? AND meeting_occurrence_id = ?
                """,
                MAPPER,
                tenantId.value(),
                meetingOccurrenceId
        );
    }

    private static final RowMapper<ArtifactMetadata> MAPPER = (rs, rowNum) -> new ArtifactMetadata(
            rs.getObject("id", UUID.class),
            TenantId.of(rs.getObject("tenant_id", UUID.class)),
            Optional.ofNullable(rs.getObject("meeting_occurrence_id", UUID.class)),
            Optional.ofNullable(rs.getObject("note_id", UUID.class)),
            Optional.ofNullable(rs.getObject("note_version_id", UUID.class)),
            ArtifactKind.valueOf(rs.getString("artifact_kind")),
            rs.getString("storage_key"),
            rs.getString("content_type"),
            Optional.ofNullable(rs.getObject("content_length_bytes", Long.class)),
            Optional.ofNullable(rs.getString("checksum_sha256")),
            JdbcInstant.get(rs, "created_at")
    );
}
