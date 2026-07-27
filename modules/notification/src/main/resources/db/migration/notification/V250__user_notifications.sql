-- In-app user notifications (portal bell feed)
CREATE TABLE IF NOT EXISTS notification.user_notifications (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    recipient_oid   TEXT NOT NULL,
    type            TEXT NOT NULL,
    title           TEXT NOT NULL,
    body            TEXT NOT NULL DEFAULT '',
    href            TEXT NOT NULL,
    dedupe_key      TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    read_at         TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_notifications_dedupe
    ON notification.user_notifications (tenant_id, recipient_oid, type, dedupe_key);

CREATE INDEX IF NOT EXISTS idx_user_notifications_recipient
    ON notification.user_notifications (tenant_id, recipient_oid, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_notifications_unread
    ON notification.user_notifications (tenant_id, recipient_oid)
    WHERE read_at IS NULL;

COMMENT ON TABLE notification.user_notifications IS 'Portal in-app notification feed; deduped per recipient';
