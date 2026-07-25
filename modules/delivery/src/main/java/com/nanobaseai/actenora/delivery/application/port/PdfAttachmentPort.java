package com.nanobaseai.actenora.delivery.application.port;

import com.nanobaseai.actenora.delivery.domain.PdfAttachmentDecision;
import com.nanobaseai.actenora.delivery.domain.DeliveryPolicySnapshot;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves PDF attachment bytes according to policy (template renderer integration).
 */
public interface PdfAttachmentPort {

    PdfAttachmentDecision decide(TenantId tenantId, UUID noteVersionId, DeliveryPolicySnapshot policy);

    Optional<byte[]> loadPdfBytes(TenantId tenantId, PdfAttachmentDecision decision);
}
