-- Widen evidence subject types for expanded minutes entities (issues/proposals/facts).
ALTER TABLE meetingintelligence.evidence_links
    DROP CONSTRAINT IF EXISTS chk_evidence_subject_type;

ALTER TABLE meetingintelligence.evidence_links
    ADD CONSTRAINT chk_evidence_subject_type CHECK (
        subject_type IN (
            'DECISION',
            'ACTION_ITEM',
            'RISK',
            'COMMITMENT',
            'OPEN_QUESTION',
            'NOTE_VERSION',
            'ISSUE',
            'PROPOSAL',
            'IMPORTANT_FACT'
        )
    );
