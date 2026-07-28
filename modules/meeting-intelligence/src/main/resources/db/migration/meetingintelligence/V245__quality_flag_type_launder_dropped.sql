-- Persist type-laundering scrub drops as a first-class quality code.
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
            'TYPE_LAUNDER_DROPPED',
            'OTHER'
        )
    );
