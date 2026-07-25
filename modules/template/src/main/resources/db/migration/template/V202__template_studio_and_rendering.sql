-- FAZ 19: Template Studio + document rendering
CREATE TABLE IF NOT EXISTS template.meeting_template (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    name                 VARCHAR(255) NOT NULL,
    published_version_id UUID,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_meeting_template_tenant
    ON template.meeting_template (tenant_id);

CREATE TABLE IF NOT EXISTS template.template_version (
    id                   UUID PRIMARY KEY,
    template_id          UUID NOT NULL REFERENCES template.meeting_template (id),
    tenant_id            UUID NOT NULL,
    version_number       INTEGER NOT NULL,
    status               VARCHAR(32) NOT NULL,
    design_schema_json   TEXT,
    content_schema_json  TEXT,
    changelog            TEXT NOT NULL DEFAULT '',
    created_at           TIMESTAMPTZ NOT NULL,
    published_at         TIMESTAMPTZ,
    updated_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_template_version_number UNIQUE (template_id, version_number),
    CONSTRAINT chk_template_version_number CHECK (version_number >= 1)
);

CREATE INDEX IF NOT EXISTS idx_template_version_tenant
    ON template.template_version (tenant_id);

CREATE TABLE IF NOT EXISTS template.note_template_lock (
    tenant_id            UUID NOT NULL,
    note_id              UUID NOT NULL,
    template_version_id  UUID NOT NULL REFERENCES template.template_version (id),
    locked_at            TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, note_id)
);

CREATE TABLE IF NOT EXISTS template.render_job (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    note_id              UUID NOT NULL,
    template_version_id  UUID NOT NULL REFERENCES template.template_version (id),
    format               VARCHAR(16) NOT NULL,
    content_hash_sha256  CHAR(64) NOT NULL,
    content_json         TEXT NOT NULL,
    status               VARCHAR(32) NOT NULL,
    attempt_count        INTEGER NOT NULL DEFAULT 0,
    max_attempts         INTEGER NOT NULL DEFAULT 3,
    last_error           TEXT,
    rendered_document_id UUID,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    completed_at         TIMESTAMPTZ,
    CONSTRAINT uq_render_job_idempotency UNIQUE (tenant_id, content_hash_sha256)
);

CREATE INDEX IF NOT EXISTS idx_render_job_pending
    ON template.render_job (status, created_at);

CREATE TABLE IF NOT EXISTS template.rendered_document (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    render_job_id        UUID NOT NULL REFERENCES template.render_job (id),
    note_id              UUID NOT NULL,
    template_version_id  UUID NOT NULL,
    format               VARCHAR(16) NOT NULL,
    content_hash_sha256  CHAR(64) NOT NULL,
    storage_key          VARCHAR(1024) NOT NULL,
    size_bytes           BIGINT NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_rendered_document_job UNIQUE (render_job_id)
);

CREATE INDEX IF NOT EXISTS idx_rendered_document_tenant
    ON template.rendered_document (tenant_id);
