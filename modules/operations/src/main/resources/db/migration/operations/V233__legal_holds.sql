-- FAZ 27: legal hold registry (blocks retention deletion while active)
CREATE TABLE IF NOT EXISTS operations.legal_holds (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    resource_type    TEXT NOT NULL,
    resource_id      TEXT NOT NULL,
    reason           TEXT NOT NULL,
    placed_by_user_id UUID,
    placed_at        TIMESTAMPTZ NOT NULL,
    released_at      TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_legal_holds_tenant_resource
    ON operations.legal_holds (tenant_id, resource_type, resource_id)
    WHERE released_at IS NULL;

COMMENT ON TABLE operations.legal_holds IS 'FAZ 27 legal hold preparation; active rows block retention deletion';
