-- FAZ 20: delivery requests, recipients, attempts, provider messages, policy snapshots, DLQ
CREATE TABLE IF NOT EXISTS delivery.requests (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    note_version_id     UUID NOT NULL,
    approval_id         UUID NOT NULL,
    recipient_id        UUID NOT NULL,
    recipient_email     VARCHAR(320) NOT NULL,
    recipient_kind      VARCHAR(32) NOT NULL,
    recipient_name      VARCHAR(255) NOT NULL DEFAULT '',
    status              VARCHAR(32) NOT NULL,
    subject             VARCHAR(998) NOT NULL,
    body_text           TEXT NOT NULL,
    policy_snapshot     JSONB NOT NULL,
    pdf_attach          BOOLEAN NOT NULL DEFAULT FALSE,
    pdf_document_id     UUID,
    pdf_object_key      VARCHAR(1024),
    signed_portal_url   TEXT,
    signed_portal_exp   TIMESTAMPTZ,
    signed_portal_fp    VARCHAR(64),
    next_attempt_at     TIMESTAMPTZ,
    dead_letter_id      UUID,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_delivery_note_recipient UNIQUE (tenant_id, note_version_id, recipient_email)
);

CREATE INDEX IF NOT EXISTS idx_delivery_requests_due
    ON delivery.requests (status, next_attempt_at)
    WHERE status = 'QUEUED';

CREATE TABLE IF NOT EXISTS delivery.attempts (
    id                  UUID PRIMARY KEY,
    delivery_request_id UUID NOT NULL REFERENCES delivery.requests (id),
    attempt_number      INT NOT NULL,
    status              VARCHAR(32) NOT NULL,
    started_at          TIMESTAMPTZ NOT NULL,
    finished_at         TIMESTAMPTZ,
    failure_code        VARCHAR(64),
    failure_detail      TEXT,
    CONSTRAINT uq_delivery_attempt UNIQUE (delivery_request_id, attempt_number)
);

CREATE TABLE IF NOT EXISTS delivery.provider_messages (
    id                  UUID PRIMARY KEY,
    attempt_id          UUID NOT NULL REFERENCES delivery.attempts (id),
    provider_type       VARCHAR(64) NOT NULL,
    provider_message_id VARCHAR(255) NOT NULL,
    acceptance_status   VARCHAR(32) NOT NULL,
    accepted_at         TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    raw_status_code     VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS delivery.dead_letters (
    id                  UUID PRIMARY KEY,
    delivery_request_id UUID NOT NULL,
    tenant_id           UUID NOT NULL,
    note_version_id     UUID NOT NULL,
    recipient_email     VARCHAR(320) NOT NULL,
    failure_code        VARCHAR(64) NOT NULL,
    failure_detail      TEXT,
    attempts            INT NOT NULL,
    dead_lettered_at    TIMESTAMPTZ NOT NULL,
    replayed_at         TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS delivery.adapter_configs (
    id              UUID PRIMARY KEY,
    tenant_id       UUID,
    provider_type   VARCHAR(64) NOT NULL,
    config_json     JSONB NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
