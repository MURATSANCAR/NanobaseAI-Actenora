-- Object-storage artifact registry (MinIO/S3 keys). Source of truth for key ownership,
-- content type, and retention — blob bytes live in object storage only.

CREATE TABLE IF NOT EXISTS meetingintelligence.artifact_metadata (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    meeting_occurrence_id   UUID,
    note_id                 UUID,
    note_version_id         UUID,
    artifact_kind           VARCHAR(64) NOT NULL,
    storage_key             VARCHAR(1024) NOT NULL,
    content_type            VARCHAR(255) NOT NULL,
    content_length_bytes    BIGINT,
    checksum_sha256         VARCHAR(64),
    created_at              TIMESTAMPTZ NOT NULL,
    UNIQUE (tenant_id, storage_key)
);

CREATE INDEX IF NOT EXISTS idx_artifact_metadata_tenant_occurrence
    ON meetingintelligence.artifact_metadata (tenant_id, meeting_occurrence_id);

CREATE INDEX IF NOT EXISTS idx_artifact_metadata_tenant_kind
    ON meetingintelligence.artifact_metadata (tenant_id, artifact_kind);
