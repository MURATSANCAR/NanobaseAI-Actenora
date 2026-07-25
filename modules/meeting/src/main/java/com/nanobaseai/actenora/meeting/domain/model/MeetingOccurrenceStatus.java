package com.nanobaseai.actenora.meeting.domain.model;

/**
 * Lifecycle status of a meeting occurrence.
 */
public enum MeetingOccurrenceStatus {
    DRAFT,
    SCHEDULED,
    IN_PROGRESS,
    ENDED,
    CANCELLED;

    public boolean isTerminal() {
        return this == ENDED || this == CANCELLED;
    }
}
