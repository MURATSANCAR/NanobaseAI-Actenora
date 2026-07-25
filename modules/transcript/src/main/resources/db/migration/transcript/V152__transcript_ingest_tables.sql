-- Transcript ingest tables (FAZ 8). Runs after V150 schema bootstrap.
CREATE SCHEMA IF NOT EXISTS transcript;

CREATE TABLE IF NOT EXISTS transcript.transcripts (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    meeting_occurrence_id   UUID NOT NULL,
    source                  VARCHAR(64) NOT NULL,
    external_transcript_id  VARCHAR(512),
    language                VARCHAR(32),
    source_format           VARCHAR(32) NOT NULL,
    raw_storage_key         VARCHAR(1024) NOT NULL,
    normalized_storage_key  VARCHAR(1024),
    content_hash            CHAR(64) NOT NULL,
    status                  VARCHAR(64) NOT NULL,
    fetched_at              TIMESTAMPTZ NOT NULL,
    normalized_at           TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_transcript_tenant_content_hash UNIQUE (tenant_id, content_hash)
);

CREATE INDEX IF NOT EXISTS idx_transcripts_tenant_occurrence
    ON transcript.transcripts (tenant_id, meeting_occurrence_id);

CREATE INDEX IF NOT EXISTS idx_transcripts_tenant_status
    ON transcript.transcripts (tenant_id, status);

CREATE TABLE IF NOT EXISTS transcript.transcript_segments (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    transcript_id           UUID NOT NULL,
    sequence                INT NOT NULL,
    speaker_id              VARCHAR(128),
    speaker_display_name    VARCHAR(512),
    start_offset_ms         BIGINT NOT NULL,
    end_offset_ms           BIGINT NOT NULL,
    content                 TEXT NOT NULL,
    content_hash            CHAR(64) NOT NULL,
    CONSTRAINT uq_segment_transcript_sequence UNIQUE (transcript_id, sequence),
    CONSTRAINT fk_segment_transcript
        FOREIGN KEY (transcript_id) REFERENCES transcript.transcripts (id)
);

CREATE INDEX IF NOT EXISTS idx_segments_tenant_transcript
    ON transcript.transcript_segments (tenant_id, transcript_id);
