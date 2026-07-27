package com.nanobaseai.actenora.delivery.application.port;

import com.nanobaseai.actenora.delivery.domain.DeliveryDeadLetter;
import com.nanobaseai.actenora.delivery.domain.DeliveryRequest;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryRequestRepository {

    DeliveryRequest save(DeliveryRequest request);

    Optional<DeliveryRequest> findById(TenantId tenantId, UUID id);

    Optional<DeliveryRequest> findByNoteVersionAndRecipient(
            TenantId tenantId,
            UUID noteVersionId,
            String recipientEmail
    );

    List<DeliveryRequest> findByNoteVersion(TenantId tenantId, UUID noteVersionId);

    /**
     * FAZ 31 — resolve a request by provider-side message id within a tenant.
     */
    Optional<DeliveryRequest> findByProviderMessageId(TenantId tenantId, String providerMessageId);

    List<DeliveryRequest> findDue(Instant now, int limit);

    void saveDeadLetter(DeliveryDeadLetter deadLetter);

    Optional<DeliveryDeadLetter> findDeadLetter(UUID id);

    List<DeliveryDeadLetter> listOpenDeadLetters(int limit);
}
