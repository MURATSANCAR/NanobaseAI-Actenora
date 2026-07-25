-- FAZ 21: calendar sync cursors, Graph subscriptions, notification idempotency.
CREATE TABLE IF NOT EXISTS microsoftconnection.calendar_sync_cursor (
    tenant_id       UUID NOT NULL,
    user_id         VARCHAR(320) NOT NULL,
    delta_link      TEXT,
    next_link       TEXT,
    updated_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, user_id)
);

CREATE TABLE IF NOT EXISTS microsoftconnection.graph_subscription (
    tenant_id               UUID NOT NULL,
    subscription_id         VARCHAR(128) NOT NULL,
    resource                TEXT NOT NULL,
    change_type             VARCHAR(128),
    notification_url        TEXT,
    client_state            VARCHAR(255),
    expiration_date_time    TIMESTAMPTZ NOT NULL,
    application_id          VARCHAR(128),
    PRIMARY KEY (tenant_id, subscription_id)
);

CREATE INDEX IF NOT EXISTS idx_graph_subscription_expiry
    ON microsoftconnection.graph_subscription (expiration_date_time);

CREATE TABLE IF NOT EXISTS microsoftconnection.notification_inbox (
    consumer_name       VARCHAR(128) NOT NULL,
    notification_id     VARCHAR(256) NOT NULL,
    claimed_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (consumer_name, notification_id)
);
