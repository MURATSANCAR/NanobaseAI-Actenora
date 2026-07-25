-- FAZ 9: tenant dictionary + normalization run persistence
CREATE TABLE IF NOT EXISTS transcript.tenant_dictionaries (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    revision        BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_tenant_dictionaries_tenant_name
    ON transcript.tenant_dictionaries (tenant_id, name);

CREATE TABLE IF NOT EXISTS transcript.dictionary_entries (
    id              UUID PRIMARY KEY,
    dictionary_id   UUID NOT NULL REFERENCES transcript.tenant_dictionaries (id),
    tenant_id       UUID NOT NULL,
    kind            VARCHAR(32) NOT NULL,
    canonical       VARCHAR(512) NOT NULL,
    aliases_json    TEXT NOT NULL DEFAULT '[]',
    external_ref    VARCHAR(512),
    active          BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS ix_dictionary_entries_dictionary
    ON transcript.dictionary_entries (dictionary_id);

CREATE TABLE IF NOT EXISTS transcript.normalization_runs (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    transcript_id               UUID NOT NULL,
    normalization_version       VARCHAR(128) NOT NULL,
    dictionary_revision         BIGINT NOT NULL,
    status                      VARCHAR(32) NOT NULL,
    normalized_transcript_hash  CHAR(64),
    metrics_json                TEXT,
    failure_code                VARCHAR(64),
    failure_message             TEXT,
    requested_at                TIMESTAMPTZ NOT NULL,
    completed_at                TIMESTAMPTZ,
    version                     BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_normalization_runs_transcript_version
    ON transcript.normalization_runs (tenant_id, transcript_id, normalization_version);

CREATE TABLE IF NOT EXISTS transcript.normalization_issues (
    id                      UUID PRIMARY KEY,
    run_id                  UUID NOT NULL REFERENCES transcript.normalization_runs (id),
    tenant_id               UUID NOT NULL,
    issue_type              VARCHAR(64) NOT NULL,
    message                 TEXT NOT NULL,
    sequence_no             INT,
    original_segment_id     UUID,
    blocking                BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS ix_normalization_issues_run
    ON transcript.normalization_issues (run_id);

CREATE TABLE IF NOT EXISTS transcript.speaker_resolutions (
    id                      UUID PRIMARY KEY,
    run_id                  UUID NOT NULL REFERENCES transcript.normalization_runs (id),
    tenant_id               UUID NOT NULL,
    original_segment_id     UUID NOT NULL,
    raw_display_name        VARCHAR(512),
    status                  VARCHAR(32) NOT NULL,
    resolved_entry_id       UUID,
    resolved_canonical      VARCHAR(512),
    candidate_entry_ids_json TEXT NOT NULL DEFAULT '[]'
);

CREATE INDEX IF NOT EXISTS ix_speaker_resolutions_run
    ON transcript.speaker_resolutions (run_id);

CREATE TABLE IF NOT EXISTS transcript.normalized_segments (
    id                          UUID PRIMARY KEY,
    run_id                      UUID NOT NULL REFERENCES transcript.normalization_runs (id),
    tenant_id                   UUID NOT NULL,
    original_segment_id         UUID NOT NULL,
    sequence_no                 INT NOT NULL,
    speaker_id                  VARCHAR(128),
    speaker_display_name        VARCHAR(512),
    start_offset_ms             BIGINT NOT NULL,
    end_offset_ms               BIGINT NOT NULL,
    original_content            TEXT NOT NULL,
    normalized_content          TEXT NOT NULL,
    normalized_content_hash     CHAR(64) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_normalized_segments_run_sequence
    ON transcript.normalized_segments (run_id, sequence_no);
