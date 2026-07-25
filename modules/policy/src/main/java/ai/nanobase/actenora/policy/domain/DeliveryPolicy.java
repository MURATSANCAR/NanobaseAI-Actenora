package ai.nanobase.actenora.policy.domain;

/**
 * Rules for outbound delivery of meeting intelligence artifacts.
 */
public record DeliveryPolicy(boolean externalDeliveryAllowed, boolean requireApproval) {

    public static DeliveryPolicy systemDefaults() {
        return new DeliveryPolicy(true, true);
    }
}
