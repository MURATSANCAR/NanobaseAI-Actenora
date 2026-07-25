-- FAZ 7: meeting series continuity identifiers + relations
-- Join URL is stored on occurrences for display only; it is not a uniqueness key.

CREATE TABLE IF NOT EXISTS meeting.meeting_relations (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    source_occurrence_id    UUID NOT NULL,
    target_occurrence_id    UUID NOT NULL,
    relation_type           VARCHAR(64) NOT NULL,
    created_by              VARCHAR(255) NOT NULL,
    suggestion_id           UUID,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_meeting_relations_distinct CHECK (source_occurrence_id <> target_occurrence_id),
    CONSTRAINT chk_meeting_relations_type CHECK (
        relation_type IN (
            'SAME_SERIES',
            'SAME_BUSINESS_CONTEXT',
            'FOLLOW_UP',
            'MANUAL',
            'AI_SUGGESTED',
            'SUPERSEDES',
            'RELATED'
        )
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_meeting_relations_directed
    ON meeting.meeting_relations (tenant_id, source_occurrence_id, target_occurrence_id, relation_type)
    WHERE relation_type IN ('FOLLOW_UP', 'SUPERSEDES');

CREATE UNIQUE INDEX IF NOT EXISTS uq_meeting_relations_undirected_low_high
    ON meeting.meeting_relations (
        tenant_id,
        LEAST(source_occurrence_id, target_occurrence_id),
        GREATEST(source_occurrence_id, target_occurrence_id),
        relation_type
    )
    WHERE relation_type NOT IN ('FOLLOW_UP', 'SUPERSEDES');

CREATE INDEX IF NOT EXISTS idx_meeting_relations_tenant_source
    ON meeting.meeting_relations (tenant_id, source_occurrence_id);

CREATE INDEX IF NOT EXISTS idx_meeting_relations_tenant_target
    ON meeting.meeting_relations (tenant_id, target_occurrence_id);

CREATE TABLE IF NOT EXISTS meeting.meeting_relation_suggestions (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    source_occurrence_id    UUID NOT NULL,
    target_occurrence_id    UUID NOT NULL,
    proposed_type           VARCHAR(64) NOT NULL,
    confidence              NUMERIC(5, 4) NOT NULL,
    reason                  TEXT NOT NULL,
    status                  VARCHAR(32) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    decided_at              TIMESTAMPTZ,
    decided_by              VARCHAR(255),
    CONSTRAINT chk_meeting_relation_suggestions_distinct CHECK (source_occurrence_id <> target_occurrence_id),
    CONSTRAINT chk_meeting_relation_suggestions_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_meeting_relation_suggestions_confidence CHECK (confidence >= 0 AND confidence <= 1)
);

CREATE INDEX IF NOT EXISTS idx_meeting_relation_suggestions_tenant_status
    ON meeting.meeting_relation_suggestions (tenant_id, status);

-- Continuity identity columns expected on occurrence table (FAZ 6 owner).
-- Additive safety net if core occurrence table already exists without them.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'meeting' AND table_name = 'meeting_occurrences'
    ) THEN
        ALTER TABLE meeting.meeting_occurrences
            ADD COLUMN IF NOT EXISTS graph_event_immutable_id VARCHAR(512),
            ADD COLUMN IF NOT EXISTS ical_uid VARCHAR(512),
            ADD COLUMN IF NOT EXISTS original_start_at TIMESTAMPTZ,
            ADD COLUMN IF NOT EXISTS join_web_url TEXT;

        CREATE UNIQUE INDEX IF NOT EXISTS uq_meeting_occurrences_tenant_immutable_id
            ON meeting.meeting_occurrences (tenant_id, graph_event_immutable_id)
            WHERE graph_event_immutable_id IS NOT NULL;

        CREATE UNIQUE INDEX IF NOT EXISTS uq_meeting_occurrences_continuity
            ON meeting.meeting_occurrences (tenant_id, ical_uid, original_start_at)
            WHERE ical_uid IS NOT NULL AND original_start_at IS NOT NULL;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'meeting' AND table_name = 'meeting_series'
    ) THEN
        ALTER TABLE meeting.meeting_series
            ADD COLUMN IF NOT EXISTS graph_series_master_id VARCHAR(512);

        CREATE UNIQUE INDEX IF NOT EXISTS uq_meeting_series_tenant_graph_master
            ON meeting.meeting_series (tenant_id, graph_series_master_id)
            WHERE graph_series_master_id IS NOT NULL;
    END IF;
END $$;
