package com.nanobaseai.actenora.transcript.domain.normalization;

/**
 * Deterministic issue codes raised during parse / normalize.
 */
public enum NormalizationIssueType {
    MALFORMED_TIMESTAMP,
    INVALID_TIME_RANGE,
    OVERLAPPING_SEGMENTS,
    DUPLICATE_SEGMENT,
    AMBIGUOUS_SPEAKER,
    UNKNOWN_SPEAKER,
    MISSING_SPEAKER,
    DICTIONARY_MISS,
    EMPTY_CONTENT
}
