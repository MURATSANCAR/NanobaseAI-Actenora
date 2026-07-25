-- Schema ownership: approval owned exclusively by approval module.
CREATE SCHEMA IF NOT EXISTS approval;

CREATE TABLE IF NOT EXISTS approval.outbox_messages (
    id              UUID PRIMARY KEY,
    event_type      VARCHAR(255) NOT NULL,
    payload         TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS approval.inbox_messages (
    id              UUID PRIMARY KEY,
    event_type      VARCHAR(255) NOT NULL,
    payload         TEXT NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS approval.requests (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    status          VARCHAR(32) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
