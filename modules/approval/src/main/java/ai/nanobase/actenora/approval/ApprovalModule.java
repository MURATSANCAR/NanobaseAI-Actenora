package ai.nanobase.actenora.approval;

/**
 * Module identity for the approval bounded context.
 */
public final class ApprovalModule {

    public static final String NAME = "approval";

    private ApprovalModule() {
    }

    public static String name() {
        return NAME;
    }
}
