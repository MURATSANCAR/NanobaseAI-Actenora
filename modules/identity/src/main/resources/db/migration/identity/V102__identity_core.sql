-- FAZ 4: users and role bindings (PostgreSQL is source of truth)
CREATE TABLE IF NOT EXISTS identity.users (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    entra_object_id     TEXT NOT NULL,
    email               TEXT NOT NULL,
    display_name        TEXT NOT NULL,
    status              TEXT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT identity_user_status_chk CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_identity_user_entra_object
    ON identity.users (entra_object_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_identity_user_tenant_email
    ON identity.users (tenant_id, lower(email));

CREATE INDEX IF NOT EXISTS idx_identity_user_tenant
    ON identity.users (tenant_id);

CREATE TABLE IF NOT EXISTS identity.user_roles (
    user_id     UUID NOT NULL REFERENCES identity.users (id) ON DELETE CASCADE,
    tenant_id   UUID NOT NULL,
    role_code   TEXT NOT NULL,
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    granted_by  UUID,
    PRIMARY KEY (user_id, role_code),
    CONSTRAINT identity_role_code_chk CHECK (role_code IN (
        'SUPER_ADMIN',
        'TENANT_ADMIN',
        'MEETING_OWNER',
        'APPROVER',
        'PARTICIPANT',
        'AUDITOR',
        'OPERATIONS'
    ))
);

CREATE INDEX IF NOT EXISTS idx_identity_user_roles_tenant
    ON identity.user_roles (tenant_id);

COMMENT ON TABLE identity.users IS 'Actenora users keyed by Entra oid; tenant_id is membership home';
COMMENT ON TABLE identity.user_roles IS 'Role bindings; permissions are derived via RolePermissionCatalog in code';
