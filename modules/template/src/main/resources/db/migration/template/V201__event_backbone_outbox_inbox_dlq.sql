
-- FAZ 10: transactional outbox / inbox / DLQ backbone (ADR-004).
CREATE TABLE IF NOT EXISTS template.outbox_event (
    id               UUID PRIMARY KEY,
    aggregate_type   VARCHAR(128) NOT NULL,
    aggregate_id     VARCHAR(128) NOT NULL,
    tenant_id        UUID NOT NULL,
    event_type       VARCHAR(255) NOT NULL,
    event_version    INT NOT NULL,
    payload_json     TEXT NOT NULL,
    correlation_id   UUID NOT NULL,
    causation_id     UUID,
    trace_id         VARCHAR(128),
    occurred_at      TIMESTAMPTZ NOT NULL,
    published_at     TIMESTAMPTZ,
    status           VARCHAR(32) NOT NULL,
    attempt_count    INT NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ NOT NULL,
    failure_code     VARCHAR(64),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_template_outbox_due
    ON template.outbox_event (status, next_attempt_at);

CREATE INDEX IF NOT EXISTS idx_template_outbox_tenant_due
    ON template.outbox_event (tenant_id, status, next_attempt_at);

CREATE TABLE IF NOT EXISTS template.inbox_event (
    consumer_name    VARCHAR(128) NOT NULL,
    event_id         UUID NOT NULL,
    received_at      TIMESTAMPTZ NOT NULL,
    processed_at     TIMESTAMPTZ,
    status           VARCHAR(32) NOT NULL,
    failure_code     VARCHAR(64),
    PRIMARY KEY (consumer_name, event_id)
);

CREATE INDEX IF NOT EXISTS idx_template_inbox_status
    ON template.inbox_event (status, received_at);

CREATE TABLE IF NOT EXISTS template.dead_letter_event (
    id               UUID PRIMARY KEY,
    source           VARCHAR(32) NOT NULL,
    event_id         UUID NOT NULL,
    consumer_name    VARCHAR(128),
    event_type       VARCHAR(255) NOT NULL,
    event_version    INT NOT NULL,
    payload_json     TEXT NOT NULL,
    failure_code     VARCHAR(64) NOT NULL,
    failure_detail   TEXT,
    correlation_id   UUID,
    tenant_id        UUID,
    attempts         INT NOT NULL,
    dead_lettered_at TIMESTAMPTZ NOT NULL,
    replayed_at      TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_template_dlq_open
    ON template.dead_letter_event (dead_lettered_at);
