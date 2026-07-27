CREATE TABLE IF NOT EXISTS microsoftconnection.mailbox_sync_work (
    tenant_id         UUID NOT NULL,
    mailbox_user_id   VARCHAR(128) NOT NULL,
    status            VARCHAR(32) NOT NULL,
    attempt_count     INT NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMPTZ NOT NULL,
    claimed_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    failure_code      VARCHAR(512),
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, mailbox_user_id),
    CONSTRAINT chk_mailbox_sync_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'RETRY', 'COMPLETED')
    )
);

CREATE INDEX IF NOT EXISTS idx_mailbox_sync_work_due
    ON microsoftconnection.mailbox_sync_work (status, next_attempt_at);
