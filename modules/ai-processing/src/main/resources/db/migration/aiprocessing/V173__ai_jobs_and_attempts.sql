-- FAZ 12: AI jobs, attempts, and selected route audit
CREATE TABLE IF NOT EXISTS aiprocessing.ai_jobs (
    id                       UUID PRIMARY KEY,
    tenant_id                UUID NOT NULL,
    meeting_occurrence_id    UUID NOT NULL,
    transcript_id            UUID NOT NULL,
    task_type                VARCHAR(128) NOT NULL,
    priority                 VARCHAR(32) NOT NULL,
    status                   VARCHAR(32) NOT NULL,
    requested_capability     VARCHAR(64) NOT NULL,
    selected_model_id        UUID,
    selected_deployment_id   UUID,
    selected_route_reason    TEXT,
    selected_route_rejects   TEXT,
    prompt_version           VARCHAR(64) NOT NULL,
    schema_version           VARCHAR(64) NOT NULL,
    input_token_count        INTEGER,
    output_token_count       INTEGER,
    queued_at                TIMESTAMPTZ NOT NULL,
    started_at               TIMESTAMPTZ,
    completed_at             TIMESTAMPTZ,
    deadline_at              TIMESTAMPTZ NOT NULL,
    correlation_id           UUID NOT NULL,
    language                 VARCHAR(16) NOT NULL DEFAULT 'tr',
    context_size             INTEGER NOT NULL DEFAULT 0,
    fallback_permitted       BOOLEAN NOT NULL DEFAULT TRUE,
    admin_override_model_id  UUID,
    admin_override_deployment_id UUID,
    attempt_count            INTEGER NOT NULL DEFAULT 0,
    version                  BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS aiprocessing.ai_attempts (
    id                       UUID PRIMARY KEY,
    ai_job_id                UUID NOT NULL REFERENCES aiprocessing.ai_jobs (id),
    attempt_number           INTEGER NOT NULL,
    model_definition_id      UUID NOT NULL,
    model_deployment_id      UUID NOT NULL,
    status                   VARCHAR(32) NOT NULL,
    latency_ms               BIGINT,
    input_tokens             INTEGER,
    output_tokens            INTEGER,
    retryable                BOOLEAN NOT NULL DEFAULT FALSE,
    failure_category         VARCHAR(64),
    failure_detail_safe      TEXT,
    started_at               TIMESTAMPTZ NOT NULL,
    completed_at             TIMESTAMPTZ,
    CONSTRAINT uq_ai_attempt_job_number UNIQUE (ai_job_id, attempt_number)
);

CREATE INDEX IF NOT EXISTS idx_ai_jobs_tenant_status
    ON aiprocessing.ai_jobs (tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_ai_jobs_status_queued
    ON aiprocessing.ai_jobs (status, queued_at);

CREATE INDEX IF NOT EXISTS idx_ai_jobs_correlation
    ON aiprocessing.ai_jobs (tenant_id, correlation_id);

CREATE INDEX IF NOT EXISTS idx_ai_attempts_job
    ON aiprocessing.ai_attempts (ai_job_id);
