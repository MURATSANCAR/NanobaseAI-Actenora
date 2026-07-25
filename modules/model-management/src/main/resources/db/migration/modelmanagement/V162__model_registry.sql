-- FAZ 11: Model Registry + Capability Registry
CREATE TABLE IF NOT EXISTS modelmanagement.model_definition (
    id                   UUID PRIMARY KEY,
    model_key            VARCHAR(128) NOT NULL,
    display_name         VARCHAR(255) NOT NULL,
    provider_type        VARCHAR(64) NOT NULL,
    served_model_id      VARCHAR(255) NOT NULL,
    model_family         VARCHAR(128) NOT NULL,
    parameter_size       VARCHAR(64),
    quantization         VARCHAR(64),
    context_window       INTEGER NOT NULL,
    max_output_tokens    INTEGER NOT NULL,
    supported_languages  TEXT NOT NULL,
    status               VARCHAR(32) NOT NULL,
    priority             INTEGER NOT NULL DEFAULT 100,
    quality_score        DOUBLE PRECISION NOT NULL DEFAULT 0,
    speed_score          DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_model_definition_key UNIQUE (model_key),
    CONSTRAINT chk_model_context_window CHECK (context_window > 0),
    CONSTRAINT chk_model_max_output CHECK (max_output_tokens > 0 AND max_output_tokens <= context_window)
);

CREATE TABLE IF NOT EXISTS modelmanagement.model_capability (
    model_definition_id  UUID NOT NULL REFERENCES modelmanagement.model_definition (id),
    capability           VARCHAR(64) NOT NULL,
    quality_score        DOUBLE PRECISION NOT NULL DEFAULT 0,
    speed_score          DOUBLE PRECISION NOT NULL DEFAULT 0,
    min_context_required INTEGER NOT NULL DEFAULT 0,
    enabled              BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (model_definition_id, capability),
    CONSTRAINT chk_cap_min_context CHECK (min_context_required >= 0)
);

CREATE TABLE IF NOT EXISTS modelmanagement.model_deployment (
    id                   UUID PRIMARY KEY,
    model_definition_id  UUID NOT NULL REFERENCES modelmanagement.model_definition (id),
    deployment_key       VARCHAR(128) NOT NULL,
    endpoint             VARCHAR(512) NOT NULL,
    node_name            VARCHAR(128) NOT NULL,
    zone                 VARCHAR(64) NOT NULL,
    hardware_type        VARCHAR(64) NOT NULL,
    gpu_type             VARCHAR(64),
    gpu_count            INTEGER NOT NULL DEFAULT 0,
    cpu_count            INTEGER NOT NULL DEFAULT 0,
    memory_gb            INTEGER NOT NULL DEFAULT 0,
    max_concurrency      INTEGER NOT NULL DEFAULT 1,
    status               VARCHAR(32) NOT NULL,
    last_heartbeat_at    TIMESTAMPTZ,
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_model_deployment_key UNIQUE (deployment_key),
    CONSTRAINT chk_deployment_concurrency CHECK (max_concurrency > 0)
);

CREATE INDEX IF NOT EXISTS idx_model_definition_status
    ON modelmanagement.model_definition (status);

CREATE INDEX IF NOT EXISTS idx_model_deployment_model
    ON modelmanagement.model_deployment (model_definition_id);

CREATE INDEX IF NOT EXISTS idx_model_deployment_status
    ON modelmanagement.model_deployment (status);
