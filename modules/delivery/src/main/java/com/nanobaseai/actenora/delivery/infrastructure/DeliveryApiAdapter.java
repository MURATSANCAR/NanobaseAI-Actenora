package com.nanobaseai.actenora.delivery.infrastructure;

import com.nanobaseai.actenora.approval.api.ApprovalApi;
import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.delivery.api.DeliveryApi;
import com.nanobaseai.actenora.delivery.api.DeliveryOrderView;
import com.nanobaseai.actenora.delivery.api.DeliveryRequestId;
import com.nanobaseai.actenora.delivery.api.DeliveryRequestStatusView;
import com.nanobaseai.actenora.delivery.application.DeliveryDispatcherService;
import com.nanobaseai.actenora.delivery.application.EnqueueDeliveryCommand;
import com.nanobaseai.actenora.delivery.application.EnqueueDeliveryResult;
import com.nanobaseai.actenora.delivery.application.ExternalDeliveryService;
import com.nanobaseai.actenora.delivery.application.port.DeliveryRequestRepository;
import com.nanobaseai.actenora.delivery.domain.DeliveryPolicySnapshot;
import com.nanobaseai.actenora.delivery.domain.DeliveryRecipient;
import com.nanobaseai.actenora.delivery.domain.DeliveryStatus;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
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

    @Override
    public EnqueueDeliveryResult enqueueDraftOrganizerNotification(
            UUID tenantId,
            UUID noteVersionId,
            String recipientEmail,
            String recipientDisplayName,
            String subject,
            String bodyText
    ) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(noteVersionId, "noteVersionId");
        Objects.requireNonNull(recipientEmail, "recipientEmail");
        TenantId tid = TenantId.of(tenantId);
        // Synthetic approval id = noteVersionId for draft path (requireApproval=false).
        ApprovalId synthetic = ApprovalId.of(noteVersionId);
        String display = recipientDisplayName == null || recipientDisplayName.isBlank()
                ? recipientEmail
                : recipientDisplayName;
        return dispatcher.enqueue(new EnqueueDeliveryCommand(
                tid,
                noteVersionId,
                synthetic,
                List.of(DeliveryRecipient.internal(recipientEmail.trim(), display)),
                DeliveryPolicySnapshot.draftOrganizer(),
                subject == null ? "Draft meeting minutes" : subject,
                bodyText == null ? "" : bodyText,
                "system:draft-organizer"
        ));
    }

    @Override
    public EnqueueDeliveryResult enqueueMeetingEndedOrganizerNotification(
            UUID tenantId,
            UUID meetingOccurrenceId,
            String recipientEmail,
            String recipientDisplayName,
            String subject,
            String bodyText
    ) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        Objects.requireNonNull(recipientEmail, "recipientEmail");
        TenantId tid = TenantId.of(tenantId);
        ApprovalId synthetic = ApprovalId.of(meetingOccurrenceId);
        String display = recipientDisplayName == null || recipientDisplayName.isBlank()
                ? recipientEmail
                : recipientDisplayName;
        return dispatcher.enqueue(new EnqueueDeliveryCommand(
                tid,
                meetingOccurrenceId,
                synthetic,
                List.of(DeliveryRecipient.internal(recipientEmail.trim(), display)),
                DeliveryPolicySnapshot.meetingEndedOrganizer(),
                subject == null ? "Toplantınız bitti · Rapor hazırlanıyor" : subject,
                bodyText == null ? "" : bodyText,
                "system:meeting-ended"
        ));
    }

    @Override
    public List<DeliveryRequestStatusView> listByNoteVersion(UUID tenantId, UUID noteVersionId) {
        TenantId tid = TenantId.of(tenantId);
        return repository.findByNoteVersion(tid, noteVersionId).stream()
                .map(r -> new DeliveryRequestStatusView(
                        r.id(),
                        r.noteVersionId(),
                        r.intent(),
                        r.status(),
                        r.recipient().email(),
                        r.createdAt(),
                        r.updatedAt()
                ))
                .toList();
    }
}
