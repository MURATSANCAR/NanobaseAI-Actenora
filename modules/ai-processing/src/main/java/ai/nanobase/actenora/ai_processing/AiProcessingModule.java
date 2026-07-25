package ai.nanobase.actenora.ai_processing;

/**
 * Module identity for the ai-processing bounded context.
 */
public final class AiProcessingModule {

    public static final String NAME = "ai-processing";

    private AiProcessingModule() {
    }

    public static String name() {
        return NAME;
    }
}
