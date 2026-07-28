package com.nanobaseai.actenora.meetingintelligence.domain.model;

public enum QualityFlagCode {
    MISSING_EVIDENCE,
    LOW_CONFIDENCE,
    SCHEMA_WARNING,
    HUMAN_CORRECTION,
    SYNTHESIS_FALLBACK,
    AUDIT_FALLBACK,
    TYPE_LAUNDER_DROPPED,
    OTHER
}
