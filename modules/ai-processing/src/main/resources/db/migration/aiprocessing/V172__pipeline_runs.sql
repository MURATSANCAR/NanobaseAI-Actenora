-- FAZ 14: store prompt/model versions and pipeline metrics per run.
CREATE TABLE IF NOT EXISTS aiprocessing.pipeline_runs (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    transcript_id           UUID NOT NULL,
    meeting_occurrence_id   UUID NOT NULL,
    prompt_version_id       VARCHAR(128) NOT NULL,
    model_version           VARCHAR(128) NOT NULL,
    status                  VARCHAR(32) NOT NULL,
    failure_category        VARCHAR(64),
    failure_message         TEXT,
    permanent_failure       BOOLEAN NOT NULL DEFAULT FALSE,
    requires_manual_review  BOOLEAN NOT NULL DEFAULT FALSE,
    input_token_count       BIGINT NOT NULL DEFAULT 0,
    output_token_count      BIGINT NOT NULL DEFAULT 0,
    duration_ms             BIGINT NOT NULL DEFAULT 0,
    chunk_count             INT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pipeline_runs_tenant_transcript
    ON aiprocessing.pipeline_runs (tenant_id, transcript_id);
