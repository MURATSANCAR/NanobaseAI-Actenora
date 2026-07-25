-- FAZ 17: Evidence validation / AI quality gate (append-only history).
CREATE TABLE IF NOT EXISTS meetingintelligence.validation_runs (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    meeting_occurrence_id   UUID NOT NULL,
    source_extraction_id    UUID NOT NULL,
    computed_outcome        VARCHAR(64) NOT NULL,
    engine_version          VARCHAR(32) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_validation_runs_extraction
    ON meetingintelligence.validation_runs (tenant_id, source_extraction_id, created_at);

CREATE TABLE IF NOT EXISTS meetingintelligence.validation_rule_results (
    id                  UUID PRIMARY KEY,
    validation_run_id   UUID NOT NULL REFERENCES meetingintelligence.validation_runs (id),
    tenant_id           UUID NOT NULL,
    rule_id             VARCHAR(128) NOT NULL,
    rule_version        VARCHAR(32) NOT NULL,
    verdict             VARCHAR(16) NOT NULL,
    message             TEXT NOT NULL,
    candidate_key       VARCHAR(256),
    detail              TEXT
);

CREATE INDEX IF NOT EXISTS idx_validation_rule_results_run
    ON meetingintelligence.validation_rule_results (validation_run_id);

CREATE TABLE IF NOT EXISTS meetingintelligence.quality_gate_decisions (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    validation_run_id       UUID NOT NULL REFERENCES meetingintelligence.validation_runs (id),
    outcome                 VARCHAR(64) NOT NULL,
    overridden              BOOLEAN NOT NULL DEFAULT FALSE,
    override_actor          VARCHAR(256),
    override_reason         TEXT,
    original_outcome        VARCHAR(64),
    decided_at              TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_quality_gate_decision_run
    ON meetingintelligence.quality_gate_decisions (validation_run_id);

CREATE TABLE IF NOT EXISTS meetingintelligence.manual_review_cases (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    validation_run_id           UUID NOT NULL REFERENCES meetingintelligence.validation_runs (id),
    quality_gate_decision_id    UUID NOT NULL REFERENCES meetingintelligence.quality_gate_decisions (id),
    meeting_occurrence_id       UUID NOT NULL,
    reason                      TEXT NOT NULL,
    status                      VARCHAR(64) NOT NULL,
    resolved_by                 VARCHAR(256),
    resolution_note             TEXT,
    created_at                  TIMESTAMPTZ NOT NULL,
    resolved_at                 TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_manual_review_open
    ON meetingintelligence.manual_review_cases (tenant_id, status);
