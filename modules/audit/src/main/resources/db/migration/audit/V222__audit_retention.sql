-- FAZ 27: audit archive eligibility marker (never DELETE from audit.entries)
ALTER TABLE audit.entries
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_audit_entries_archive_eligible
    ON audit.entries (occurred_at)
    WHERE archived_at IS NULL;

COMMENT ON COLUMN audit.entries.archived_at IS
    'Set when exported to cold archive; application must never DELETE rows';
