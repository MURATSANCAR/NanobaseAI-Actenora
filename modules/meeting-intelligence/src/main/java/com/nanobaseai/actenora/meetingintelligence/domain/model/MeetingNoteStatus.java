package com.nanobaseai.actenora.meetingintelligence.domain.model;

/**
 * Lifecycle of a meeting note version under approval/versioning (FAZ 18).
 */
public enum MeetingNoteStatus {
    DRAFT,
    PENDING_APPROVAL,
    CHANGES_REQUESTED,
    APPROVED,
    REJECTED,
    SUPERSEDED
}
