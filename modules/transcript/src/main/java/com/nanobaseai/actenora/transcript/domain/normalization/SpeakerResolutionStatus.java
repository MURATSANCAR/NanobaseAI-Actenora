package com.nanobaseai.actenora.transcript.domain.normalization;

/**
 * Outcome of matching a raw speaker label against the tenant dictionary.
 * Ambiguous matches are never auto-finalized.
 */
public enum SpeakerResolutionStatus {
    RESOLVED_EXACT,
    RESOLVED_ALIAS,
    UNRESOLVED,
    AMBIGUOUS,
    MISSING_SPEAKER
}
