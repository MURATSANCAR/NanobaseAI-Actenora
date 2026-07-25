package ai.nanobase.actenora.meeting_intelligence;

/**
 * Module identity for the meeting-intelligence bounded context.
 */
public final class MeetingIntelligenceModule {

    public static final String NAME = "meeting-intelligence";

    private MeetingIntelligenceModule() {
    }

    public static String name() {
        return NAME;
    }
}
