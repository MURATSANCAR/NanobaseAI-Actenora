package ai.nanobase.actenora.tenant;

/**
 * Module identity for the tenant bounded context.
 */
public final class TenantModule {

    public static final String NAME = "tenant";

    private TenantModule() {
    }

    public static String name() {
        return NAME;
    }
}
