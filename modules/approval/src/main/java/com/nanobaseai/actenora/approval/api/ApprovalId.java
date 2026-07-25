package com.nanobaseai.actenora.approval.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Opaque approval reference for consumers (e.g. delivery) that must not see entities.
 */
public record ApprovalId(UUID value) {

    public ApprovalId {
        Objects.requireNonNull(value, "value");
    }

    public static ApprovalId of(UUID value) {
        return new ApprovalId(value);
    }
}
