package ai.nanobase.actenora.transcript;

/**
 * Module identity for the transcript bounded context.
 */
public final class TranscriptModule {

    public static final String NAME = "transcript";

    private TranscriptModule() {
    }

    public static String name() {
        return NAME;
    }
}
