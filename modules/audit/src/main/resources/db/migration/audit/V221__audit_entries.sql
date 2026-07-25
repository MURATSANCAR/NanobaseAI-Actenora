-- FAZ 5: immutable audit entries (application must never UPDATE or DELETE)
CREATE TABLE IF NOT EXISTS audit.entries (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    actor_user_id   UUID,
    action          TEXT NOT NULL,
    resource_type   TEXT NOT NULL,
    resource_id     TEXT,
    correlation_id  TEXT,
    trace_id        TEXT,
    ip_address      TEXT,
    user_agent      TEXT,
    occurred_at     TIMESTAMPTZ NOT NULL,
    metadata_json   JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_audit_entries_tenant_occurred
    ON audit.entries (tenant_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_entries_correlation
    ON audit.entries (correlation_id);

COMMENT ON TABLE audit.entries IS 'Immutable append-only audit log; application must never UPDATE or DELETE';
COMMENT ON COLUMN audit.entries.metadata_json IS 'Must not contain transcript, private_note, or raw_prompt payloads';
