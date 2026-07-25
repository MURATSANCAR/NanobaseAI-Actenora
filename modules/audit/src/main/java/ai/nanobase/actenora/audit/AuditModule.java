package ai.nanobase.actenora.audit;

/**
 * Module identity for the audit bounded context.
 */
public final class AuditModule {

    public static final String NAME = "audit";

    private AuditModule() {
    }

    public static String name() {
        return NAME;
    }
}
