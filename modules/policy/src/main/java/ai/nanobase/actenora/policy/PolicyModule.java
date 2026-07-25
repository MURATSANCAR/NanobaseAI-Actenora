package ai.nanobase.actenora.policy;

/**
 * Module identity for the policy bounded context.
 */
public final class PolicyModule {

    public static final String NAME = "policy";

    private PolicyModule() {
    }

    public static String name() {
        return NAME;
    }
}
