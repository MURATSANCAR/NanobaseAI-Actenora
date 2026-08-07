-- Runtime-editable Microsoft Graph / Teams connection settings.
-- A single global row (one Azure app registration per deployment) mirrors the boot-time
-- properties; the admin portal edits it and hot-applies to the live Graph client.
-- The client secret is stored as ciphertext (AES-GCM) when an encryption key is configured.

CREATE TABLE IF NOT EXISTS microsoftconnection.graph_connection_settings (
    scope_key               VARCHAR(64) PRIMARY KEY,
    enabled                 BOOLEAN NOT NULL DEFAULT TRUE,
    graph_base_url          VARCHAR(512) NOT NULL,
    authority_host          VARCHAR(512) NOT NULL,
    azure_tenant_id         VARCHAR(128),
    client_id               VARCHAR(128),
    graph_scope             VARCHAR(512) NOT NULL,
    auth_mode               VARCHAR(32) NOT NULL,
    client_secret_cipher    TEXT,
    certificate_pem_path    VARCHAR(1024),
    private_key_pem_path     VARCHAR(1024),
    default_mailbox_user_id VARCHAR(256),
    updated_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_graph_conn_auth_mode CHECK (auth_mode IN ('CERTIFICATE', 'CLIENT_SECRET'))
);
