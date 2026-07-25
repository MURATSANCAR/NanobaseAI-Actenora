package com.nanobaseai.actenora.delivery.infrastructure.pdf;

import com.nanobaseai.actenora.delivery.application.port.PdfAttachmentPort;
import com.nanobaseai.actenora.delivery.domain.DeliveryPolicySnapshot;
import com.nanobaseai.actenora.delivery.domain.PdfAttachmentDecision;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory PDF attachment policy adapter (template renderer wires real bytes later).
 */
public final class InMemoryPdfAttachmentPort implements PdfAttachmentPort {

    private final Map<String, byte[]> pdfByNote = new ConcurrentHashMap<>();
    private final Map<String, UUID> documentIdByNote = new ConcurrentHashMap<>();

    public void putPdf(TenantId tenantId, UUID noteVersionId, UUID documentId, byte[] bytes) {
        String key = key(tenantId, noteVersionId);
        pdfByNote.put(key, bytes.clone());
        documentIdByNote.put(key, documentId);
    }

    @Override
    public PdfAttachmentDecision decide(TenantId tenantId, UUID noteVersionId, DeliveryPolicySnapshot policy) {
        if (!policy.shouldAttachPdf()) {
            String reason = policy.sensitiveMeeting()
                    ? "sensitive_meeting_uses_signed_portal_link"
                    : "pdf_attachment_disabled_by_policy";
            return PdfAttachmentDecision.deny(reason);
        }
        String lookup = key(tenantId, noteVersionId);
        UUID documentId = documentIdByNote.get(lookup);
        if (documentId == null) {
            return PdfAttachmentDecision.deny("pdf_not_rendered");
        }
        return PdfAttachmentDecision.attach(
                documentId,
                "tenants/" + tenantId.value() + "/notes/" + noteVersionId + ".pdf"
        );
    }

    @Override
    public Optional<byte[]> loadPdfBytes(TenantId tenantId, PdfAttachmentDecision decision) {
        if (!decision.attach() || decision.renderedDocumentId() == null) {
            return Optional.empty();
        }
        return pdfByNote.entrySet().stream()
                .filter(e -> decision.renderedDocumentId().equals(documentIdByNote.get(e.getKey())))
                .map(Map.Entry::getValue)
                .map(byte[]::clone)
                .findFirst();
    }

    private static String key(TenantId tenantId, UUID id) {
        return tenantId.value() + "|" + id;
    }
}
