package ai.nanobase.actenora.meeting;

/**
 * Module identity for the meeting bounded context.
 */
public final class MeetingModule {

    public static final String NAME = "meeting";

    private MeetingModule() {
    }

    public static String name() {
        return NAME;
    }
}
