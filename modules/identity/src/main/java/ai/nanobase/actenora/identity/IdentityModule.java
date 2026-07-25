package ai.nanobase.actenora.identity;

/**
 * Module identity for the identity bounded context.
 */
public final class IdentityModule {

    public static final String NAME = "identity";

    private IdentityModule() {
    }

    public static String name() {
        return NAME;
    }
}
