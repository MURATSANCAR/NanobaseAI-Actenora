package com.nanobaseai.actenora.delivery.api;

import java.util.Objects;
import java.util.UUID;

/** Opaque delivery request id for cross-module references. */
public record DeliveryRequestId(UUID value) {

    public DeliveryRequestId {
        Objects.requireNonNull(value, "value");
    }

    public static DeliveryRequestId of(UUID value) {
        return new DeliveryRequestId(value);
    }

    public static DeliveryRequestId random() {
        return new DeliveryRequestId(UUID.randomUUID());
    }
}
