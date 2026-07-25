package ai.nanobase.actenora.model_management;

/**
 * Module identity for the model-management bounded context.
 */
public final class ModelManagementModule {

    public static final String NAME = "model-management";

    private ModelManagementModule() {
    }

    public static String name() {
        return NAME;
    }
}
