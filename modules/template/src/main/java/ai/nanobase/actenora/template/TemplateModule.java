package ai.nanobase.actenora.template;

/**
 * Module identity for the template bounded context.
 */
public final class TemplateModule {

    public static final String NAME = "template";

    private TemplateModule() {
    }

    public static String name() {
        return NAME;
    }
}
