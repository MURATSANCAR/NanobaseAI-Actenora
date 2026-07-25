-- FAZ 6: Meeting core domain tables (schema meeting)

CREATE TABLE IF NOT EXISTS meeting.business_contexts (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    type            VARCHAR(128) NOT NULL,
    reference_code  VARCHAR(128) NOT NULL,
    name            VARCHAR(512) NOT NULL,
    description     TEXT,
    status          VARCHAR(32) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_business_contexts_tenant_ref
    ON meeting.business_contexts (tenant_id, reference_code);

CREATE TABLE IF NOT EXISTS meeting.meeting_series (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    business_context_id     UUID NOT NULL,
    graph_series_master_id  VARCHAR(512),
    organizer_user_id       UUID NOT NULL,
    title                   VARCHAR(1024) NOT NULL,
    meeting_type            VARCHAR(64) NOT NULL,
    status                  VARCHAR(32) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_meeting_series_tenant
    ON meeting.meeting_series (tenant_id);

CREATE TABLE IF NOT EXISTS meeting.meeting_occurrences (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    meeting_series_id           UUID NOT NULL,
    business_context_id         UUID NOT NULL,
    graph_event_immutable_id    VARCHAR(512),
    ical_uid                    VARCHAR(512),
    original_start_at           TIMESTAMPTZ,
    teams_meeting_id            VARCHAR(512),
    chat_id                     VARCHAR(512),
    join_web_url                TEXT,
    title                       VARCHAR(1024) NOT NULL,
    organizer_user_id           UUID NOT NULL,
    scheduled_start_at          TIMESTAMPTZ NOT NULL,
    scheduled_end_at            TIMESTAMPTZ NOT NULL,
    actual_start_at             TIMESTAMPTZ,
    actual_end_at               TIMESTAMPTZ,
    status                      VARCHAR(32) NOT NULL,
    processing_priority         VARCHAR(32) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_meeting_occurrence_date_range
        CHECK (scheduled_end_at > scheduled_start_at)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_meeting_occurrence_graph_identity
    ON meeting.meeting_occurrences (tenant_id, graph_event_immutable_id)
    WHERE graph_event_immutable_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_meeting_occurrence_ical_identity
    ON meeting.meeting_occurrences (tenant_id, ical_uid, original_start_at)
    WHERE ical_uid IS NOT NULL AND original_start_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_meeting_occurrence_tenant_status
    ON meeting.meeting_occurrences (tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_meeting_occurrence_business_context
    ON meeting.meeting_occurrences (tenant_id, business_context_id);

CREATE TABLE IF NOT EXISTS meeting.meeting_participants (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    meeting_occurrence_id   UUID NOT NULL,
    entra_user_id           VARCHAR(128),
    display_name            VARCHAR(512) NOT NULL,
    email                   VARCHAR(512) NOT NULL,
    participant_type        VARCHAR(64) NOT NULL,
    attendance_status       VARCHAR(64) NOT NULL,
    joined_at               TIMESTAMPTZ,
    left_at                 TIMESTAMPTZ,
    is_external             BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_meeting_participants_occurrence
    ON meeting.meeting_participants (tenant_id, meeting_occurrence_id);
