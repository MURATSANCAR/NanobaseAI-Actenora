-- FAZ 5: policy core tables (PostgreSQL is source of truth; cache is optional)
CREATE TABLE IF NOT EXISTS policy.tenant_policy_overrides (
    tenant_id                         UUID PRIMARY KEY,
    retention_json                    JSONB,
    delivery_json                     JSONB,
    model_access_json                 JSONB,
    processing_sla_json               JSONB,
    concurrency_json                  JSONB,
    external_participant_json         JSONB,
    quotas_json                       JSONB,
    daily_meeting_limit               INTEGER,
    daily_transcript_minutes          INTEGER,
    daily_input_token_limit           BIGINT,
    daily_output_token_limit          BIGINT,
    max_concurrent_ai_jobs            INTEGER,
    max_transcript_duration_minutes   INTEGER,
    max_file_size_bytes               BIGINT,
    critical_meeting_fallback_allowed BOOLEAN,
    updated_at                        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                           BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS policy.tenant_policy_materialized (
    tenant_id       UUID PRIMARY KEY,
    policy_json     JSONB NOT NULL,
    resolved_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS policy.quota_usage_daily (
    tenant_id   UUID NOT NULL,
    usage_day   DATE NOT NULL,
    dimension   TEXT NOT NULL,
    used_amount BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, usage_day, dimension)
);

CREATE TABLE IF NOT EXISTS policy.concurrency_usage (
    tenant_id              UUID PRIMARY KEY,
    concurrent_ai_jobs     INTEGER NOT NULL DEFAULT 0,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS policy.model_allowlist (
    tenant_id   UUID NOT NULL,
    model_key   TEXT NOT NULL,
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (tenant_id, model_key)
);

COMMENT ON TABLE policy.tenant_policy_overrides IS 'Partial tenant overrides layered on system defaults';
COMMENT ON COLUMN policy.tenant_policy_overrides.critical_meeting_fallback_allowed IS 'Permits fallback model on CRITICAL meetings when primary is unavailable';
