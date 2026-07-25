package ai.nanobase.actenora.delivery;

/**
 * Module identity for the delivery bounded context.
 */
public final class DeliveryModule {

    public static final String NAME = "delivery";

    private DeliveryModule() {
    }

    public static String name() {
        return NAME;
    }
}
