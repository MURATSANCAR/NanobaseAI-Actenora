-- FAZ 25: Operations Center alert history and metric snapshots.
CREATE TABLE IF NOT EXISTS operations.ops_alerts (
    id              UUID PRIMARY KEY,
    alert_type      VARCHAR(64) NOT NULL,
    severity        VARCHAR(32) NOT NULL,
    title           VARCHAR(512) NOT NULL,
    detail          TEXT NOT NULL,
    raised_at       TIMESTAMPTZ NOT NULL,
    acknowledged    BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS operations.certificate_watch (
    name            VARCHAR(255) PRIMARY KEY,
    subject         VARCHAR(512) NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ops_alerts_raised_at ON operations.ops_alerts (raised_at DESC);
