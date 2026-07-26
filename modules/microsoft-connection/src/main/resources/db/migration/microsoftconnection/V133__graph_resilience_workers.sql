CREATE UNIQUE INDEX IF NOT EXISTS uq_graph_subscription_subscription_id
    ON microsoftconnection.graph_subscription (subscription_id);

CREATE TABLE IF NOT EXISTS microsoftconnection.transcript_poll_work (
    tenant_id               UUID NOT NULL,
    meeting_occurrence_id   UUID NOT NULL,
    status                  VARCHAR(32) NOT NULL,
    attempt_count           INT NOT NULL DEFAULT 0,
    next_attempt_at         TIMESTAMPTZ NOT NULL,
    claimed_at              TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    failure_code            VARCHAR(512),
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, meeting_occurrence_id),
    CONSTRAINT chk_transcript_poll_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'RETRY', 'COMPLETED', 'DEAD_LETTER')
    )
);

CREATE INDEX IF NOT EXISTS idx_transcript_poll_work_due
    ON microsoftconnection.transcript_poll_work (status, next_attempt_at);

CREATE TABLE IF NOT EXISTS microsoftconnection.worker_lease (
    lease_name       VARCHAR(128) PRIMARY KEY,
    owner_id         VARCHAR(128) NOT NULL,
    locked_until     TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL
);
