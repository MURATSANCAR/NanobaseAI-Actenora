package com.nanobaseai.actenora.aiprocessing.domain.pipeline.filter;

/**
 * Extraction item kinds evaluated by {@link MeetingItemPolicy}.
 */
public enum MeetingItemType {
    DECISION,
    ACTION,
    COMMITMENT,
    OPEN_QUESTION,
    TOPIC,
    IMPORTANT_FACT,
    PROPOSAL,
    ISSUE,
    RISK
}
