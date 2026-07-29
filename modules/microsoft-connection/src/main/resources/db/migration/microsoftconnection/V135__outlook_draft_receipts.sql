CREATE TABLE IF NOT EXISTS microsoftconnection.outlook_draft_receipts (
    tenant_id              UUID NOT NULL,
    idempotency_key        VARCHAR(255) NOT NULL,
    status                 VARCHAR(32) NOT NULL,
    provider_message_id    TEXT,
    web_link               TEXT,
    claimed_at             TIMESTAMPTZ NOT NULL,
    completed_at           TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_outlook_draft_receipts_status
    ON microsoftconnection.outlook_draft_receipts (tenant_id, status);
