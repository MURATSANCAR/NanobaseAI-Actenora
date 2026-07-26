-- FAZ 18: approval lifecycle status on meeting note versions (additive on V181 schema).
ALTER TABLE meetingintelligence.meeting_note_versions
    ADD COLUMN IF NOT EXISTS approval_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT';

CREATE INDEX IF NOT EXISTS idx_mi_note_versions_approval_status
    ON meetingintelligence.meeting_note_versions (approval_status);

COMMENT ON COLUMN meetingintelligence.meeting_note_versions.approval_status IS
    'DRAFT | PENDING_APPROVAL | CHANGES_REQUESTED | APPROVED | REJECTED | SUPERSEDED';
