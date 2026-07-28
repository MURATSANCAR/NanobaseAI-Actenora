package com.nanobaseai.actenora.aiprocessing.domain.pipeline.speechact;

/**
 * Compact speech-act taxonomy for meeting noise / type-laundering policy.
 */
public enum MeetingSpeechAct {
    STATUS_QUO,
    EXPLICIT_DECISION,
    DISCUSSION_PROMPT,
    NOTE_INSTRUCTION,
    CLOSING_META,
    PROPOSAL_CUE,
    ACTION_REQUEST,
    UNKNOWN
}
