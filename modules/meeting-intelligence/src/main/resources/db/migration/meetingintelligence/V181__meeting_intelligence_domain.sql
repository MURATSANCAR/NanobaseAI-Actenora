-- FAZ 16: Meeting Intelligence domain tables
CREATE TABLE IF NOT EXISTS meetingintelligence.meeting_notes (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    meeting_occurrence_id   UUID NOT NULL,
    current_version_id      UUID NOT NULL,
    current_version_number  INTEGER NOT NULL,
    review_status           VARCHAR(32) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_meeting_notes_review_status CHECK (review_status IN ('ACTIVE', 'MANUAL_REVIEW'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_meeting_notes_tenant_occurrence
    ON meetingintelligence.meeting_notes (tenant_id, meeting_occurrence_id);

CREATE INDEX IF NOT EXISTS idx_meeting_notes_tenant
    ON meetingintelligence.meeting_notes (tenant_id);

CREATE TABLE IF NOT EXISTS meetingintelligence.meeting_note_versions (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    note_id                 UUID NOT NULL,
    version_number          INTEGER NOT NULL,
    executive_summary       TEXT NOT NULL,
    source                  VARCHAR(32) NOT NULL,
    model_id                VARCHAR(255),
    prompt_version_id       VARCHAR(255),
    schema_id               VARCHAR(255),
    ai_confidence           DOUBLE PRECISION,
    correction_reason       TEXT,
    created_by_user_id      UUID,
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_note_version UNIQUE (tenant_id, note_id, version_number),
    CONSTRAINT chk_note_version_source CHECK (source IN ('AI_MAPPING', 'HUMAN_EDIT')),
    CONSTRAINT chk_note_version_number CHECK (version_number >= 1),
    CONSTRAINT chk_note_version_confidence CHECK (ai_confidence IS NULL OR (ai_confidence >= 0 AND ai_confidence <= 1))
);

CREATE TABLE IF NOT EXISTS meetingintelligence.decisions (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    note_id                     UUID NOT NULL,
    note_version_id             UUID NOT NULL,
    text                        TEXT NOT NULL,
    supersedes_decision_id      UUID,
    superseded_by_decision_id   UUID,
    requires_manual_review      BOOLEAN NOT NULL DEFAULT FALSE,
    ai_confidence               DOUBLE PRECISION,
    human_approval_status       VARCHAR(32) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_decisions_human_approval CHECK (human_approval_status IN ('NONE', 'APPROVED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_decisions_note ON meetingintelligence.decisions (tenant_id, note_id);

CREATE TABLE IF NOT EXISTS meetingintelligence.action_items (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    note_id                 UUID NOT NULL,
    note_version_id         UUID NOT NULL,
    text                    TEXT NOT NULL,
    owner                   VARCHAR(255),
    due_date                DATE,
    status                  VARCHAR(32) NOT NULL,
    requires_manual_review  BOOLEAN NOT NULL DEFAULT FALSE,
    ai_confidence           DOUBLE PRECISION,
    human_approval_status   VARCHAR(32) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_action_items_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_action_items_human_approval CHECK (human_approval_status IN ('NONE', 'APPROVED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_action_items_note ON meetingintelligence.action_items (tenant_id, note_id);

CREATE TABLE IF NOT EXISTS meetingintelligence.risks (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    note_id                 UUID NOT NULL,
    note_version_id         UUID NOT NULL,
    text                    TEXT NOT NULL,
    requires_manual_review  BOOLEAN NOT NULL DEFAULT FALSE,
    ai_confidence           DOUBLE PRECISION,
    human_approval_status   VARCHAR(32) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_risks_human_approval CHECK (human_approval_status IN ('NONE', 'APPROVED', 'REJECTED'))
);

CREATE TABLE IF NOT EXISTS meetingintelligence.commitments (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    note_id                 UUID NOT NULL,
    note_version_id         UUID NOT NULL,
    text                    TEXT NOT NULL,
    owner                   VARCHAR(255),
    confirmation_status     VARCHAR(32) NOT NULL,
    requires_manual_review  BOOLEAN NOT NULL DEFAULT FALSE,
    ai_confidence           DOUBLE PRECISION,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    decided_at              TIMESTAMPTZ,
    decided_by_user_id      UUID,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_commitments_status CHECK (
        confirmation_status IN ('PENDING_CONFIRMATION', 'CONFIRMED', 'REJECTED')
    )
);

CREATE TABLE IF NOT EXISTS meetingintelligence.open_questions (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    note_id                 UUID NOT NULL,
    note_version_id         UUID NOT NULL,
    text                    TEXT NOT NULL,
    requires_manual_review  BOOLEAN NOT NULL DEFAULT FALSE,
    ai_confidence           DOUBLE PRECISION,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS meetingintelligence.evidence_links (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    note_id                 UUID NOT NULL,
    note_version_id         UUID NOT NULL,
    subject_type            VARCHAR(32) NOT NULL,
    subject_id              UUID NOT NULL,
    evidence_segment_id     VARCHAR(255) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_evidence_subject_type CHECK (
        subject_type IN ('DECISION', 'ACTION_ITEM', 'RISK', 'COMMITMENT', 'OPEN_QUESTION', 'NOTE_VERSION')
    )
);

CREATE INDEX IF NOT EXISTS idx_evidence_links_subject
    ON meetingintelligence.evidence_links (tenant_id, subject_id);

CREATE TABLE IF NOT EXISTS meetingintelligence.quality_flags (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    note_id                 UUID NOT NULL,
    note_version_id         UUID NOT NULL,
    code                    VARCHAR(64) NOT NULL,
    detail                  TEXT NOT NULL DEFAULT '',
    subject_type            VARCHAR(32),
    subject_id              UUID,
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_quality_flag_code CHECK (
        code IN ('MISSING_EVIDENCE', 'LOW_CONFIDENCE', 'SCHEMA_WARNING', 'HUMAN_CORRECTION', 'OTHER')
    )
);

CREATE INDEX IF NOT EXISTS idx_quality_flags_note
    ON meetingintelligence.quality_flags (tenant_id, note_id);
