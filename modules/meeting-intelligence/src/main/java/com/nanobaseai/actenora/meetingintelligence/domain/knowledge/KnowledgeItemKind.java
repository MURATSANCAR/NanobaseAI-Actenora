package com.nanobaseai.actenora.meetingintelligence.domain.knowledge;

/**
 * Structured item kinds eligible for knowledge indexing after human approval.
 * Raw transcript is never indexed.
 */
public enum KnowledgeItemKind {
    DECISION,
    ACTION_ITEM,
    COMMITMENT,
    RISK,
    OPEN_QUESTION
}
