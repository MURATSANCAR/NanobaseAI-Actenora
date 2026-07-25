package com.nanobaseai.actenora.meeting.domain.relation;

/**
 * Supported meeting relation kinds.
 */
public enum RelationType {
    SAME_SERIES,
    SAME_BUSINESS_CONTEXT,
    FOLLOW_UP,
    MANUAL,
    AI_SUGGESTED,
    SUPERSEDES,
    RELATED;

    public boolean isDirected() {
        return this == FOLLOW_UP || this == SUPERSEDES;
    }

    public boolean participatesInFollowUpCycles() {
        return this == FOLLOW_UP;
    }
}
