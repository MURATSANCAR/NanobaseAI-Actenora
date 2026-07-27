-- Allow pipeline soft-degrade flags to persist as first-class quality codes.
ALTER TABLE meetingintelligence.quality_flags
    DROP CONSTRAINT IF EXISTS chk_quality_flag_code;

ALTER TABLE meetingintelligence.quality_flags
    ADD CONSTRAINT chk_quality_flag_code CHECK (
        code IN (
            'MISSING_EVIDENCE',
            'LOW_CONFIDENCE',
            'SCHEMA_WARNING',
            'HUMAN_CORRECTION',
            'SYNTHESIS_FALLBACK',
            'AUDIT_FALLBACK',
            'OTHER'
        )
    );
