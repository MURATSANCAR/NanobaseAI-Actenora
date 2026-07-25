-- FAZ 18: approval lifecycle status on meeting note versions.
CREATE TABLE IF NOT EXISTS meetingintelligence.meeting_notes (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    meeting_occurrence_id   UUID NOT NULL,
    current_version_id      UUID NOT NULL,
    current_version_number  INT NOT NULL DEFAULT 0,
    review_status           VARCHAR(32) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS meetingintelligence.meeting_note_versions (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    note_id             UUID NOT NULL REFERENCES meetingintelligence.meeting_notes (id),
    version_number      INT NOT NULL,
    executive_summary   TEXT NOT NULL,
    source              VARCHAR(32) NOT NULL,
    provenance_json     JSONB,
    correction_reason   TEXT,
    created_by_user_id  UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    approval_status     VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    UNIQUE (note_id, version_number)
);

CREATE INDEX IF NOT EXISTS idx_mi_notes_tenant
    ON meetingintelligence.meeting_notes (tenant_id);

CREATE INDEX IF NOT EXISTS idx_mi_note_versions_approval_status
    ON meetingintelligence.meeting_note_versions (approval_status);

COMMENT ON COLUMN meetingintelligence.meeting_note_versions.approval_status IS
    'DRAFT | PENDING_APPROVAL | CHANGES_REQUESTED | APPROVED | REJECTED | SUPERSEDED';
