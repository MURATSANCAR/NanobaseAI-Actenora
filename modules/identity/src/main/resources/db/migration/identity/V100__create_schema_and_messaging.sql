-- Schema ownership: identity owned exclusively by identity module.
-- Other modules must not create or alter objects in this schema.
CREATE SCHEMA IF NOT EXISTS identity;

CREATE TABLE IF NOT EXISTS identity.outbox_messages (
    id              UUID PRIMARY KEY,
    event_type      VARCHAR(255) NOT NULL,
    payload         TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS identity.inbox_messages (
    id              UUID PRIMARY KEY,
    event_type      VARCHAR(255) NOT NULL,
    payload         TEXT NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ
);
