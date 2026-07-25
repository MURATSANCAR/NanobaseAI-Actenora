package com.nanobaseai.actenora.delivery.domain;

/**
 * Delivery lifecycle. {@link #PROVIDER_ACCEPTED} is not success — only {@link #DELIVERED} is.
 */
public enum DeliveryStatus {
    QUEUED,
    SENDING,
    PROVIDER_ACCEPTED,
    DELIVERED,
    DEFERRED,
    BOUNCED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == DELIVERED
                || this == BOUNCED
                || this == FAILED
                || this == CANCELLED;
    }

    public boolean isProviderAcceptedOnly() {
        return this == PROVIDER_ACCEPTED;
    }

    public boolean allowsRetry() {
        return this == DEFERRED || this == FAILED;
    }
}
