package com.nanobaseai.actenora.meetingintelligence.infrastructure;

/**
 * Marker for meeting-intelligence persistence access.
 * AI Processing must not touch classes in this package or {@code meetingintelligence} schema tables.
 */
public final class MeetingIntelligenceSchema {

    public static final String SCHEMA = "meetingintelligence";
    public static final String MEETING_NOTES_TABLE = "meetingintelligence.meeting_notes";
    public static final String MEETING_NOTE_VERSIONS_TABLE = "meetingintelligence.meeting_note_versions";
    public static final String DECISIONS_TABLE = "meetingintelligence.decisions";
    public static final String ACTION_ITEMS_TABLE = "meetingintelligence.action_items";
    public static final String RISKS_TABLE = "meetingintelligence.risks";
    public static final String COMMITMENTS_TABLE = "meetingintelligence.commitments";
    public static final String OPEN_QUESTIONS_TABLE = "meetingintelligence.open_questions";
    public static final String EVIDENCE_LINKS_TABLE = "meetingintelligence.evidence_links";
    public static final String QUALITY_FLAGS_TABLE = "meetingintelligence.quality_flags";

    /** @deprecated Placeholder from module scaffold; use domain tables above. */
    @Deprecated
    public static final String INSIGHTS_TABLE = "meetingintelligence.insights";

    private MeetingIntelligenceSchema() {
    }
}
