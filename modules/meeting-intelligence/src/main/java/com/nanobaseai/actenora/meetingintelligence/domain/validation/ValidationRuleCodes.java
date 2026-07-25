package com.nanobaseai.actenora.meetingintelligence.domain.validation;

/**
 * Stable rule identifiers and versions for the deterministic quality gate.
 */
public final class ValidationRuleCodes {

    public static final String EVIDENCE_SEGMENT_EXISTS = "EVIDENCE_SEGMENT_EXISTS";
    public static final String OFFSET_VALID = "OFFSET_VALID";
    public static final String OWNER_IS_PARTICIPANT = "OWNER_IS_PARTICIPANT";
    public static final String DUE_DATE_IN_TRANSCRIPT = "DUE_DATE_IN_TRANSCRIPT";
    public static final String AMOUNT_IN_TRANSCRIPT = "AMOUNT_IN_TRANSCRIPT";
    public static final String SUGGESTION_NOT_DECISION = "SUGGESTION_NOT_DECISION";
    public static final String DUPLICATE_CANDIDATE = "DUPLICATE_CANDIDATE";
    public static final String MARKER_PROXIMITY = "MARKER_PROXIMITY";
    public static final String SPEAKER_ATTRIBUTION_SAFE = "SPEAKER_ATTRIBUTION_SAFE";
    public static final String CONFIDENCE_THRESHOLD = "CONFIDENCE_THRESHOLD";

    public static final String VERSION_1_0_0 = "1.0.0";

    private ValidationRuleCodes() {
    }
}
