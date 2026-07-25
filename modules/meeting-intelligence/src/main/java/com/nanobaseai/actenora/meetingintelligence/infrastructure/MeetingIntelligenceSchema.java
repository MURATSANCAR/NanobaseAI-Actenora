package com.nanobaseai.actenora.meetingintelligence.infrastructure;

/**
 * Marker for meeting-intelligence persistence access.
 * AI Processing must not touch classes in this package or {@code meetingintelligence} schema tables.
 */
public final class MeetingIntelligenceSchema {

    public static final String SCHEMA = "meetingintelligence";
    public static final String INSIGHTS_TABLE = "meetingintelligence.insights";

    private MeetingIntelligenceSchema() {
    }
}
