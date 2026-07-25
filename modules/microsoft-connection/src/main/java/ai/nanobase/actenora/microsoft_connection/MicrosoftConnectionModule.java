package ai.nanobase.actenora.microsoft_connection;

/**
 * Module identity for the microsoft-connection bounded context.
 */
public final class MicrosoftConnectionModule {

    public static final String NAME = "microsoft-connection";

    private MicrosoftConnectionModule() {
    }

    public static String name() {
        return NAME;
    }
}
