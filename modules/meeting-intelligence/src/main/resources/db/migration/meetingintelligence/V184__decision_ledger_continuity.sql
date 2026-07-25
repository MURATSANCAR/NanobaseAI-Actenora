-- FAZ 23: Decision Ledger, Commitment Tracker, Continuity projections (event-sourced read models).
-- All tables live in meetingintelligence — no operational cross-schema joins.

CREATE TABLE IF NOT EXISTS meetingintelligence.ledger_events (
    event_id                UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    event_type              VARCHAR(255) NOT NULL,
    aggregate_type          VARCHAR(128) NOT NULL,
    aggregate_id            UUID NOT NULL,
    meeting_occurrence_id   UUID,
    occurred_at             TIMESTAMPTZ NOT NULL,
    sequence_no             BIGINT NOT NULL,
    payload_json            TEXT NOT NULL,
    UNIQUE (tenant_id, sequence_no)
);

CREATE INDEX IF NOT EXISTS idx_ledger_events_tenant_seq
    ON meetingintelligence.ledger_events (tenant_id, sequence_no);

CREATE TABLE IF NOT EXISTS meetingintelligence.decision_history (
    decision_id                 UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    meeting_occurrence_id       UUID NOT NULL,
    note_id                     UUID NOT NULL,
    text                        TEXT NOT NULL,
    supersedes_decision_id      UUID,
    superseded_by_decision_id   UUID,
    active                      BOOLEAN NOT NULL DEFAULT TRUE,
    recorded_at                 TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_decision_history_tenant_occurrence
    ON meetingintelligence.decision_history (tenant_id, meeting_occurrence_id);

CREATE TABLE IF NOT EXISTS meetingintelligence.commitment_confirmations (
    commitment_id           UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    meeting_occurrence_id   UUID NOT NULL,
    note_id                 UUID NOT NULL,
    text                    TEXT NOT NULL,
    owner                   VARCHAR(512),
    status                  VARCHAR(64) NOT NULL,
    due_date                DATE,
    overdue                 BOOLEAN NOT NULL DEFAULT FALSE,
    recorded_at             TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    decided_at              TIMESTAMPTZ,
    decided_by_user_id      UUID
);

CREATE INDEX IF NOT EXISTS idx_commitment_confirmations_tenant_overdue
    ON meetingintelligence.commitment_confirmations (tenant_id, overdue);

CREATE TABLE IF NOT EXISTS meetingintelligence.continuity_projections (
    meeting_occurrence_id               UUID PRIMARY KEY,
    tenant_id                           UUID NOT NULL,
    meeting_series_id                   UUID,
    business_context_id                 UUID,
    previous_occurrence_id              UUID,
    next_occurrence_id                  UUID,
    same_series_occurrence_ids_json     TEXT NOT NULL DEFAULT '[]',
    same_business_context_ids_json      TEXT NOT NULL DEFAULT '[]',
    follow_up_chain_json                TEXT NOT NULL DEFAULT '[]',
    projected_at                        TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_continuity_projections_series
    ON meetingintelligence.continuity_projections (tenant_id, meeting_series_id);

CREATE INDEX IF NOT EXISTS idx_continuity_projections_context
    ON meetingintelligence.continuity_projections (tenant_id, business_context_id);

CREATE TABLE IF NOT EXISTS meetingintelligence.meeting_briefs (
    brief_id                UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    target_occurrence_id    UUID NOT NULL,
    previous_occurrence_id  UUID,
    meeting_series_id       UUID,
    business_context_id     UUID,
    payload_json            TEXT NOT NULL,
    generated_at            TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_meeting_briefs_tenant_target
    ON meetingintelligence.meeting_briefs (tenant_id, target_occurrence_id);

CREATE TABLE IF NOT EXISTS meetingintelligence.contradiction_candidates (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    meeting_occurrence_id   UUID NOT NULL,
    left_decision_id        UUID NOT NULL,
    right_decision_id       UUID NOT NULL,
    reason                  TEXT NOT NULL,
    confidence              NUMERIC(5,4) NOT NULL,
    status                  VARCHAR(32) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    decided_at              TIMESTAMPTZ,
    decided_by              VARCHAR(256)
);

CREATE INDEX IF NOT EXISTS idx_contradiction_candidates_tenant_status
    ON meetingintelligence.contradiction_candidates (tenant_id, status);

CREATE TABLE IF NOT EXISTS meetingintelligence.continuity_relation_suggestions (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    source_occurrence_id    UUID NOT NULL,
    target_occurrence_id    UUID NOT NULL,
    proposed_relation       VARCHAR(64) NOT NULL,
    confidence              NUMERIC(5,4) NOT NULL,
    reason                  TEXT NOT NULL,
    status                  VARCHAR(32) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    decided_at              TIMESTAMPTZ,
    decided_by              VARCHAR(256)
);

CREATE INDEX IF NOT EXISTS idx_continuity_relation_suggestions_tenant_status
    ON meetingintelligence.continuity_relation_suggestions (tenant_id, status);
