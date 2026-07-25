-- Schema ownership: meetingintelligence owned exclusively by meetingintelligence module.
CREATE SCHEMA IF NOT EXISTS meetingintelligence;

CREATE TABLE IF NOT EXISTS meetingintelligence.outbox_messages (
    id              UUID PRIMARY KEY,
    event_type      VARCHAR(255) NOT NULL,
    payload         TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS meetingintelligence.inbox_messages (
    id              UUID PRIMARY KEY,
    event_type      VARCHAR(255) NOT NULL,
    payload         TEXT NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS meetingintelligence.insights (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    meeting_id      UUID NOT NULL,
    summary         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
