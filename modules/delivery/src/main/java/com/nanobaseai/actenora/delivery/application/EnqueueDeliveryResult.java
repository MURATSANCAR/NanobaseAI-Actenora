package com.nanobaseai.actenora.delivery.application;

import com.nanobaseai.actenora.delivery.api.DeliveryRequestId;
import com.nanobaseai.actenora.delivery.domain.DeliveryStatus;

import java.util.List;
import java.util.Objects;

public record EnqueueDeliveryResult(
        List<DeliveryRequestId> createdIds,
        List<DeliveryRequestId> duplicateIds
) {

    public EnqueueDeliveryResult {
        Objects.requireNonNull(createdIds, "createdIds");
        Objects.requireNonNull(duplicateIds, "duplicateIds");
        createdIds = List.copyOf(createdIds);
        duplicateIds = List.copyOf(duplicateIds);
    }

    public record Item(DeliveryRequestId id, DeliveryStatus status, boolean duplicate) {
    }
}
