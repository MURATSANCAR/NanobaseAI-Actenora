-- Discussed topics (agenda / GÖRÜŞÜLEN KONULAR) as a first-class note sub-entity.
-- Mirrors meetingintelligence.important_facts (see V242). Previously the AI pipeline folded
-- extracted topics into important_facts, which pushed agenda items into "ÖNEMLİ BULGULAR".
CREATE TABLE IF NOT EXISTS meetingintelligence.topics (
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

CREATE INDEX IF NOT EXISTS idx_topics_note
    ON meetingintelligence.topics (tenant_id, note_id);
