-- FAZ 22: in-meeting collaboration for Teams Meeting App surfaces.
CREATE TABLE IF NOT EXISTS meeting.markers (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    meeting_occurrence_id   UUID NOT NULL,
    marker_type             VARCHAR(32) NOT NULL,
    body                    TEXT NOT NULL,
    offset_ms               BIGINT NOT NULL,
    created_by_user_id      UUID NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    idempotency_key         VARCHAR(128)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_meeting_markers_idempotency
    ON meeting.markers (tenant_id, created_by_user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_meeting_markers_occurrence
    ON meeting.markers (tenant_id, meeting_occurrence_id);

CREATE TABLE IF NOT EXISTS meeting.shared_notes (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    meeting_occurrence_id   UUID NOT NULL,
    body                    TEXT NOT NULL DEFAULT '',
    created_by_user_id      UUID NOT NULL,
    updated_by_user_id      UUID NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_meeting_shared_notes_occurrence UNIQUE (tenant_id, meeting_occurrence_id)
);

CREATE TABLE IF NOT EXISTS meeting.private_notes (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    meeting_occurrence_id   UUID NOT NULL,
    owner_user_id           UUID NOT NULL,
    body                    TEXT NOT NULL DEFAULT '',
    ai_use_allowed          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_meeting_private_notes_owner UNIQUE (tenant_id, meeting_occurrence_id, owner_user_id)
);

CREATE TABLE IF NOT EXISTS meeting.agendas (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    meeting_occurrence_id   UUID NOT NULL,
    items_json              TEXT NOT NULL DEFAULT '[]',
    created_by_user_id      UUID NOT NULL,
    updated_by_user_id      UUID NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_meeting_agendas_occurrence UNIQUE (tenant_id, meeting_occurrence_id)
);

CREATE TABLE IF NOT EXISTS meeting.open_tasks (
    id                              UUID PRIMARY KEY,
    tenant_id                       UUID NOT NULL,
    meeting_occurrence_id           UUID NOT NULL,
    title                           VARCHAR(512) NOT NULL,
    assignee_user_id                UUID,
    is_open                         BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_id              UUID NOT NULL,
    created_at                      TIMESTAMPTZ NOT NULL,
    source_meeting_occurrence_id    UUID
);

CREATE INDEX IF NOT EXISTS ix_meeting_open_tasks_occurrence
    ON meeting.open_tasks (tenant_id, meeting_occurrence_id)
    WHERE is_open = TRUE;

CREATE TABLE IF NOT EXISTS meeting.collaboration_idempotency (
    tenant_id           UUID NOT NULL,
    actor_user_id       UUID NOT NULL,
    idempotency_key     VARCHAR(128) NOT NULL,
    response_ref        TEXT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, actor_user_id, idempotency_key)
);
