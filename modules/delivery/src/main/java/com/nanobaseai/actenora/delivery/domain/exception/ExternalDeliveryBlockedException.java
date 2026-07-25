package com.nanobaseai.actenora.delivery.domain.exception;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

import java.util.UUID;

public final class ExternalDeliveryBlockedException extends ActenoraException {

    public ExternalDeliveryBlockedException(UUID noteVersionId, String reason) {
        super(
                "EXTERNAL_DELIVERY_BLOCKED",
                "External delivery blocked for note version " + noteVersionId + ": " + reason
        );
    }
}
