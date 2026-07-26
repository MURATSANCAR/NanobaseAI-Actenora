-- FAZ 5 / Wave 3: DB-level enforcement — audit entries are append-only
CREATE OR REPLACE FUNCTION audit.deny_audit_entries_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit.entries is append-only; UPDATE and DELETE are forbidden';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_audit_entries_no_update ON audit.entries;
CREATE TRIGGER trg_audit_entries_no_update
    BEFORE UPDATE ON audit.entries
    FOR EACH ROW EXECUTE FUNCTION audit.deny_audit_entries_mutation();

DROP TRIGGER IF EXISTS trg_audit_entries_no_delete ON audit.entries;
CREATE TRIGGER trg_audit_entries_no_delete
    BEFORE DELETE ON audit.entries
    FOR EACH ROW EXECUTE FUNCTION audit.deny_audit_entries_mutation();

COMMENT ON FUNCTION audit.deny_audit_entries_mutation() IS 'Wave 3: reject UPDATE/DELETE on immutable audit log';
