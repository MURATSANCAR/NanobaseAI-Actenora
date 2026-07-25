-- Schema ownership: modelmanagement owned exclusively by modelmanagement module.
-- Other modules must not create or alter objects in this schema.
CREATE SCHEMA IF NOT EXISTS modelmanagement;

CREATE TABLE IF NOT EXISTS modelmanagement.outbox_messages (
    id              UUID PRIMARY KEY,
    event_type      VARCHAR(255) NOT NULL,
    payload         TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS modelmanagement.inbox_messages (
    id              UUID PRIMARY KEY,
    event_type      VARCHAR(255) NOT NULL,
    payload         TEXT NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ
);
