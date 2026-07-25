package com.nanobaseai.actenora.delivery.infrastructure;

import com.nanobaseai.actenora.approval.api.ApprovalApi;
import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.delivery.api.DeliveryApi;
import com.nanobaseai.actenora.delivery.api.DeliveryRequestId;
import com.nanobaseai.actenora.delivery.application.DeliveryDispatcherService;
import com.nanobaseai.actenora.delivery.application.EnqueueDeliveryCommand;
import com.nanobaseai.actenora.delivery.application.EnqueueDeliveryResult;
import com.nanobaseai.actenora.delivery.application.ExternalDeliveryService;
import com.nanobaseai.actenora.delivery.domain.DeliveryOrder;
import com.nanobaseai.actenora.delivery.domain.DeliveryStatus;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Composition-root adapter: FAZ 18 approval gate + FAZ 20 mail dispatcher façade.
 */
public final class DeliveryApiAdapter implements DeliveryApi {

    private final ExternalDeliveryService externalDeliveryService;
    private final DeliveryDispatcherService dispatcher;
    private final com.nanobaseai.actenora.delivery.application.port.DeliveryRequestRepository repository;

    public DeliveryApiAdapter(
            ApprovalApi approvalApi,
            Clock clock,
            DeliveryDispatcherService dispatcher,
            com.nanobaseai.actenora.delivery.application.port.DeliveryRequestRepository repository
    ) {
        this.externalDeliveryService = new ExternalDeliveryService(
                Objects.requireNonNull(approvalApi, "approvalApi"),
                Objects.requireNonNull(clock, "clock")
        );
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public DeliveryApiAdapter(
            ExternalDeliveryService externalDeliveryService,
            DeliveryDispatcherService dispatcher,
            com.nanobaseai.actenora.delivery.application.port.DeliveryRequestRepository repository
    ) {
        this.externalDeliveryService = Objects.requireNonNull(externalDeliveryService, "externalDeliveryService");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public UUID requestExternalDelivery(UUID tenantId, ApprovalId approvalId, UUID noteVersionId, String channel) {
        DeliveryOrder order = externalDeliveryService.requestExternalDelivery(
                tenantId, approvalId, noteVersionId, channel);
        return order.id();
    }

    @Override
    public EnqueueDeliveryResult enqueue(EnqueueDeliveryCommand command) {
        return dispatcher.enqueue(command);
    }

    @Override
    public Optional<DeliveryStatus> status(TenantId tenantId, DeliveryRequestId id) {
        return repository.findById(tenantId, id.value()).map(r -> r.status());
    }

    @Override
    public DeliveryStatus confirmDelivered(TenantId tenantId, DeliveryRequestId id) {
        return dispatcher.confirmDelivered(tenantId.value(), id.value());
    }
}
