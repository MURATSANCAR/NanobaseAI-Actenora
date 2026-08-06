package com.nanobaseai.actenora.aiprocessing.infrastructure.persistence;

import com.nanobaseai.actenora.aiprocessing.application.port.ProcessingArtifactRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.ProcessingArtifactExportSink;
import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingArtifact;
import com.nanobaseai.actenora.sharedkernel.persistence.jdbc.JdbcInstant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JdbcProcessingArtifactRepository implements ProcessingArtifactRepository {

    private static final RowMapper<ProcessingArtifact> ROW_MAPPER = (rs, rowNum) -> new ProcessingArtifact(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("job_id", UUID.class),
            rs.getObject("meeting_occurrence_id", UUID.class),
            rs.getString("artifact_type"),
            rs.getString("object_key"),
            rs.getString("content_hash"),
            rs.getString("content_type"),
            (Long) rs.getObject("size_bytes"),
            rs.getString("payload_json"),
            JdbcInstant.get(rs, "created_at")
    );

    private final JdbcTemplate jdbc;
    private final ProcessingArtifactExportSink exportSink;

    public JdbcProcessingArtifactRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, ProcessingArtifactExportSink.noop());
    }

    public JdbcProcessingArtifactRepository(
            JdbcTemplate jdbcTemplate,
            ProcessingArtifactExportSink exportSink
    ) {
        this.jdbc = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.exportSink = Objects.requireNonNull(exportSink, "exportSink");
    }

    @Override
    public void save(ProcessingArtifact artifact) {
        int inserted = jdbc.update(
                """
                        INSERT INTO aiprocessing.processing_artifact (
                            id, tenant_id, job_id, meeting_occurrence_id, artifact_type,
                            object_key, content_hash, content_type, size_bytes, payload_json, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                        ON CONFLICT (id) DO NOTHING
                        """,
                artifact.id(),
                artifact.tenantId(),
                artifact.jobId(),
                artifact.meetingOccurrenceId(),
                artifact.artifactType(),
                artifact.objectKey().orElse(null),
                artifact.contentHash().orElse(null),
                artifact.contentType().orElse(null),
                artifact.sizeBytes().orElse(null),
                artifact.payloadJson().orElse(null),
                JdbcInstant.toTimestamp(artifact.createdAt())
        );
        if (inserted == 1) {
            exportSink.export(artifact);
        }
    }

    @Override
    public List<ProcessingArtifact> findByJobId(UUID jobId) {
        return jdbc.query(
                """
                        SELECT id, tenant_id, job_id, meeting_occurrence_id, artifact_type,
                               object_key, content_hash, content_type, size_bytes,
                               payload_json::text AS payload_json, created_at
                        FROM aiprocessing.processing_artifact
                        WHERE job_id = ?
                        ORDER BY created_at ASC
                        """,
                ROW_MAPPER,
                jobId
        );
    }

    @Override
    public Optional<ProcessingArtifact> findLatestByMeetingAndType(
            UUID tenantId,
            UUID meetingOccurrenceId,
            String artifactType
    ) {
        return jdbc.query(
                """
                        SELECT id, tenant_id, job_id, meeting_occurrence_id, artifact_type,
                               object_key, content_hash, content_type, size_bytes,
                               payload_json::text AS payload_json, created_at
                        FROM aiprocessing.processing_artifact
                        WHERE tenant_id = ? AND meeting_occurrence_id = ? AND artifact_type = ?
                        ORDER BY created_at DESC
                        LIMIT 1
                        """,
                ROW_MAPPER,
                tenantId,
                meetingOccurrenceId,
                artifactType
        ).stream().findFirst();
    }

    @Override
    public List<ProcessingArtifact> findByParentMeetingAndType(
            UUID tenantId,
            UUID meetingOccurrenceId,
            String artifactType
    ) {
        return jdbc.query(
                """
                        SELECT id, tenant_id, job_id, meeting_occurrence_id, artifact_type,
                               object_key, content_hash, content_type, size_bytes,
                               payload_json::text AS payload_json, created_at
                        FROM aiprocessing.processing_artifact
                        WHERE tenant_id = ? AND meeting_occurrence_id = ? AND artifact_type = ?
                        ORDER BY created_at ASC
                        """,
                ROW_MAPPER,
                tenantId,
                meetingOccurrenceId,
                artifactType
        );
    }
}
