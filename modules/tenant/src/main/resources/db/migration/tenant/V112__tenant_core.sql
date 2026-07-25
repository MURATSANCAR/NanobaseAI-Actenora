-- FAZ 4: tenant registry and membership (PostgreSQL is source of truth)
CREATE TABLE IF NOT EXISTS tenant.tenants (
    id                      UUID PRIMARY KEY,
    name                    TEXT NOT NULL,
    status                  TEXT NOT NULL,
    timezone                TEXT NOT NULL DEFAULT 'UTC',
    default_language        TEXT NOT NULL DEFAULT 'en',
    retention_policy_days   INTEGER NOT NULL DEFAULT 365,
    entra_tenant_id         TEXT NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT tenant_status_chk CHECK (status IN ('PROVISIONING', 'ACTIVE', 'SUSPENDED')),
    CONSTRAINT tenant_retention_chk CHECK (retention_policy_days > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_entra_tenant_id
    ON tenant.tenants (entra_tenant_id);

CREATE INDEX IF NOT EXISTS idx_tenant_status
    ON tenant.tenants (status);

CREATE TABLE IF NOT EXISTS tenant.tenant_memberships (
    tenant_id   UUID NOT NULL REFERENCES tenant.tenants (id),
    user_id     UUID NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_tenant_membership_user
    ON tenant.tenant_memberships (user_id);

COMMENT ON TABLE tenant.tenants IS 'Actenora tenant registry; entra_tenant_id maps Entra tid claim';
COMMENT ON TABLE tenant.tenant_memberships IS 'Users who may access the tenant; roles live in identity schema';
COMMENT ON COLUMN tenant.tenants.status IS 'SUSPENDED tenants are full-blocked (see docs/security/SUSPENDED-TENANT-POLICY.md)';
