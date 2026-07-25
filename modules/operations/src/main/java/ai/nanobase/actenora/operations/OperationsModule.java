package ai.nanobase.actenora.operations;

/**
 * Module identity for the operations bounded context.
 */
public final class OperationsModule {

    public static final String NAME = "operations";

    private OperationsModule() {
    }

    public static String name() {
        return NAME;
    }
}
