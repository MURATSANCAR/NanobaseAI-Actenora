-- Expanded minutes fields: issues / proposals / important facts (+ optional attrs).
CREATE TABLE IF NOT EXISTS meetingintelligence.issues (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    note_id                 UUID NOT NULL,
    note_version_id         UUID NOT NULL,
    text                    TEXT NOT NULL,
    requires_manual_review  BOOLEAN NOT NULL DEFAULT FALSE,
    ai_confidence           DOUBLE PRECISION,
    human_approval_status   VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_issues_note
    ON meetingintelligence.issues (tenant_id, note_id);

CREATE TABLE IF NOT EXISTS meetingintelligence.proposals (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    note_id                 UUID NOT NULL,
    note_version_id         UUID NOT NULL,
    text                    TEXT NOT NULL,
    requires_manual_review  BOOLEAN NOT NULL DEFAULT FALSE,
    ai_confidence           DOUBLE PRECISION,
    human_approval_status   VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_proposals_note
    ON meetingintelligence.proposals (tenant_id, note_id);

CREATE TABLE IF NOT EXISTS meetingintelligence.important_facts (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    note_id                 UUID NOT NULL,
    note_version_id         UUID NOT NULL,
    text                    TEXT NOT NULL,
    requires_manual_review  BOOLEAN NOT NULL DEFAULT FALSE,
    ai_confidence           DOUBLE PRECISION,
    human_approval_status   VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_important_facts_note
    ON meetingintelligence.important_facts (tenant_id, note_id);

ALTER TABLE meetingintelligence.action_items
    ADD COLUMN IF NOT EXISTS owner_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS priority VARCHAR(32),
    ADD COLUMN IF NOT EXISTS relative_date VARCHAR(64);

ALTER TABLE meetingintelligence.risks
    ADD COLUMN IF NOT EXISTS likelihood VARCHAR(32),
    ADD COLUMN IF NOT EXISTS mitigation TEXT;

ALTER TABLE meetingintelligence.decisions
    ADD COLUMN IF NOT EXISTS rationale TEXT,
    ADD COLUMN IF NOT EXISTS status VARCHAR(32);
