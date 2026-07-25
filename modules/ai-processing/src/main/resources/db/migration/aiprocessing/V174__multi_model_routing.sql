-- FAZ 15: multi-model routing audit, provenance, shadow, quality metrics.
-- Schema ownership: aiprocessing.

CREATE TABLE IF NOT EXISTS aiprocessing.routing_decisions (
    id                          UUID PRIMARY KEY,
    job_id                      UUID NOT NULL,
    tenant_id                   UUID NOT NULL,
    correlation_id              UUID NOT NULL,
    task_type                   VARCHAR(64) NOT NULL,
    requested_role              VARCHAR(64) NOT NULL,
    fallback_step               VARCHAR(64) NOT NULL,
    selected_model_definition_id UUID,
    selected_deployment_id      UUID,
    selected_model_key          VARCHAR(128),
    quality_downgraded          BOOLEAN NOT NULL DEFAULT FALSE,
    requires_retry_queue        BOOLEAN NOT NULL DEFAULT FALSE,
    requires_manual_review      BOOLEAN NOT NULL DEFAULT FALSE,
    reason                      TEXT NOT NULL,
    candidates_json             TEXT NOT NULL,
    decided_at                  TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_routing_decisions_job
    ON aiprocessing.routing_decisions (job_id, decided_at);

CREATE TABLE IF NOT EXISTS aiprocessing.model_change_provenance (
    id                          UUID PRIMARY KEY,
    job_id                      UUID NOT NULL,
    routing_decision_id         UUID NOT NULL,
    from_model_definition_id    UUID,
    from_deployment_id          UUID,
    from_model_key              VARCHAR(128),
    to_model_definition_id      UUID,
    to_deployment_id            UUID,
    to_model_key                VARCHAR(128),
    fallback_step               VARCHAR(64) NOT NULL,
    quality_downgraded          BOOLEAN NOT NULL DEFAULT FALSE,
    reason                      TEXT NOT NULL,
    recorded_at                 TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_model_change_provenance_job
    ON aiprocessing.model_change_provenance (job_id, recorded_at);

CREATE TABLE IF NOT EXISTS aiprocessing.attempt_history (
    id                          UUID PRIMARY KEY,
    job_id                      UUID NOT NULL,
    routing_decision_id         UUID NOT NULL,
    attempt_number              INT NOT NULL,
    model_definition_id         UUID NOT NULL,
    deployment_id               UUID NOT NULL,
    model_key                   VARCHAR(128) NOT NULL,
    role                        VARCHAR(64) NOT NULL,
    fallback_step               VARCHAR(64) NOT NULL,
    status                      VARCHAR(32) NOT NULL,
    quality_downgraded          BOOLEAN NOT NULL DEFAULT FALSE,
    failure_category            VARCHAR(64),
    failure_detail_safe         TEXT,
    started_at                  TIMESTAMPTZ NOT NULL,
    completed_at                TIMESTAMPTZ,
    UNIQUE (job_id, attempt_number)
);

CREATE TABLE IF NOT EXISTS aiprocessing.shadow_executions (
    id                              UUID PRIMARY KEY,
    job_id                          UUID NOT NULL,
    routing_decision_id             UUID NOT NULL,
    champion_deployment_id          UUID NOT NULL,
    challenger_deployment_id        UUID NOT NULL,
    champion_model_definition_id    UUID NOT NULL,
    challenger_model_definition_id  UUID NOT NULL,
    status                          VARCHAR(32) NOT NULL,
    challenger_result_ref           TEXT,
    comparison_summary_safe         TEXT,
    created_at                      TIMESTAMPTZ NOT NULL,
    completed_at                    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_shadow_executions_job
    ON aiprocessing.shadow_executions (job_id);

CREATE TABLE IF NOT EXISTS aiprocessing.model_quality_metrics (
    model_definition_id     UUID PRIMARY KEY,
    model_key               VARCHAR(128) NOT NULL,
    role                    VARCHAR(64) NOT NULL,
    success_count           BIGINT NOT NULL DEFAULT 0,
    failure_count           BIGINT NOT NULL DEFAULT 0,
    latency_sum_ms          DOUBLE PRECISION NOT NULL DEFAULT 0,
    latency_samples         BIGINT NOT NULL DEFAULT 0,
    schema_pass_count       BIGINT NOT NULL DEFAULT 0,
    schema_total_count      BIGINT NOT NULL DEFAULT 0,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS aiprocessing.retry_queue (
    job_id                  UUID PRIMARY KEY,
    routing_decision_id     UUID NOT NULL,
    enqueued_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
