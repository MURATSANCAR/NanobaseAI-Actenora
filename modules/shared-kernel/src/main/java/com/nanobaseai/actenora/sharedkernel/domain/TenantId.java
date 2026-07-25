package com.nanobaseai.actenora.sharedkernel.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Opaque tenant identifier shared across contexts as a value type only.
 */
public record TenantId(UUID value) {

    public TenantId {
        Objects.requireNonNull(value, "value");
    }

    public static TenantId of(UUID value) {
        return new TenantId(value);
    }

    public static TenantId random() {
        return new TenantId(UUID.randomUUID());
    }
}
