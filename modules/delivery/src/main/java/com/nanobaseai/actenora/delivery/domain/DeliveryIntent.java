package com.nanobaseai.actenora.delivery.domain;

/**
 * Delivery intent channel labels used on requests/orders.
 */
public final class DeliveryIntent {

    public static final String DRAFT_ORGANIZER = "DRAFT_ORGANIZER";
    public static final String FINAL_EXTERNAL = "FINAL_EXTERNAL";
    /** Organizer status mail when a meeting occurrence transitions to ENDED. */
    public static final String MEETING_ENDED = "MEETING_ENDED";

    private DeliveryIntent() {
    }
}
