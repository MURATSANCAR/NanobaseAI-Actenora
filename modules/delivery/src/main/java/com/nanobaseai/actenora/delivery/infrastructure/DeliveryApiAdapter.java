package com.nanobaseai.actenora.delivery.infrastructure;

import com.nanobaseai.actenora.approval.api.ApprovalApi;
import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.delivery.api.DeliveryApi;
import com.nanobaseai.actenora.delivery.api.DeliveryOrderView;
import com.nanobaseai.actenora.delivery.api.DeliveryRequestId;
import com.nanobaseai.actenora.delivery.application.DeliveryDispatcherService;
import com.nanobaseai.actenora.delivery.application.EnqueueDeliveryCommand;
import com.nanobaseai.actenora.delivery.application.EnqueueDeliveryResult;
import com.nanobaseai.actenora.delivery.application.ExternalDeliveryService;
import com.nanobaseai.actenora.delivery.application.port.DeliveryRequestRepository;
import com.nanobaseai.actenora.delivery.domain.DeliveryStatus;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Composition-root adapter: FAZ 19 approval-gated orders + FAZ 20 mail dispatcher façade.
 */
public final class DeliveryApiAdapter implements DeliveryApi {

    private final ExternalDeliveryService externalDeliveryService;
    private final DeliveryDispatcherService dispatcher;
    private final DeliveryRequestRepository repository;

    public DeliveryApiAdapter(
            ExternalDeliveryService externalDeliveryService,
            DeliveryDispatcherService dispatcher,
            DeliveryRequestRepository repository
    ) {
        this.externalDeliveryService = Objects.requireNonNull(externalDeliveryService, "externalDeliveryService");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * @deprecated Prefer Spring-wired constructor with {@link ExternalDeliveryService}.
     */
    @Deprecated
    public DeliveryApiAdapter(
            ApprovalApi approvalApi,
            java.time.Clock clock,
            DeliveryDispatcherService dispatcher,
            DeliveryRequestRepository repository
    ) {
        this(new ExternalDeliveryService(approvalApi, clock), dispatcher, repository);
    }

    @Override
    public DeliveryOrderView requestExternalDelivery(
            UUID tenantId,
            ApprovalId approvalId,
            UUID noteVersionId,
            String channel
    ) {
        return DeliveryOrderView.from(externalDeliveryService.requestExternalDelivery(
                tenantId, approvalId, noteVersionId, channel));
    }

    @Override
    public Optional<DeliveryOrderView> getOrder(UUID tenantId, UUID orderId) {
        return externalDeliveryService.findOrder(tenantId, orderId).map(DeliveryOrderView::from);
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

    @Override
    public DeliveryRequestId resolveByProviderMessageId(TenantId tenantId, String providerMessageId) {
        return DeliveryRequestId.of(dispatcher.resolveByProviderMessageId(tenantId.value(), providerMessageId));
    }
}
