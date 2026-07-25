-- FAZ 18: approval workflow tables (multi-step ready; V1 uses single step).
CREATE TABLE IF NOT EXISTS approval.approval_requests (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    subject_type    VARCHAR(64) NOT NULL,
    subject_id      UUID NOT NULL,
    status          VARCHAR(32) NOT NULL,
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_approval_requests_tenant_subject
    ON approval.approval_requests (tenant_id, subject_id);

CREATE TABLE IF NOT EXISTS approval.approval_steps (
    id                      UUID PRIMARY KEY,
    approval_request_id     UUID NOT NULL REFERENCES approval.approval_requests (id),
    step_order              INT NOT NULL,
    required_approver_id    VARCHAR(255) NOT NULL,
    status                  VARCHAR(32) NOT NULL,
    decided_at              TIMESTAMPTZ,
    UNIQUE (approval_request_id, step_order)
);

CREATE TABLE IF NOT EXISTS approval.approval_decisions (
    id                      UUID PRIMARY KEY,
    approval_request_id     UUID NOT NULL REFERENCES approval.approval_requests (id),
    step_id                 UUID NOT NULL REFERENCES approval.approval_steps (id),
    decision_type           VARCHAR(32) NOT NULL,
    decided_by              VARCHAR(255) NOT NULL,
    comment                 TEXT NOT NULL DEFAULT '',
    decided_at              TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS approval.change_requests (
    id                      UUID PRIMARY KEY,
    approval_request_id     UUID NOT NULL REFERENCES approval.approval_requests (id),
    subject_id              UUID NOT NULL,
    requested_by            VARCHAR(255) NOT NULL,
    reason                  TEXT NOT NULL,
    status                  VARCHAR(32) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at             TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS approval.participant_disputes (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    subject_id              UUID NOT NULL,
    subject_type            VARCHAR(64) NOT NULL,
    participant_id          VARCHAR(255) NOT NULL,
    proposed_content        TEXT NOT NULL,
    reason                  TEXT NOT NULL,
    status                  VARCHAR(32) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at             TIMESTAMPTZ,
    resolved_by             VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_participant_disputes_subject
    ON approval.participant_disputes (tenant_id, subject_id);

COMMENT ON TABLE approval.approval_requests IS 'FAZ 18 approval aggregate; expires_at prepared for timeout workers';
COMMENT ON COLUMN approval.approval_decisions.comment IS 'First-class approval comment';
