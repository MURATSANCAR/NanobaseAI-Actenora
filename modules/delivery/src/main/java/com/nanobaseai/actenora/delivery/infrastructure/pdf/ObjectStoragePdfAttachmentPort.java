package com.nanobaseai.actenora.delivery.infrastructure.pdf;

import com.nanobaseai.actenora.delivery.application.port.PdfAttachmentPort;
import com.nanobaseai.actenora.delivery.domain.DeliveryPolicySnapshot;
import com.nanobaseai.actenora.delivery.domain.PdfAttachmentDecision;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorage;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorageException;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves PDF attachments from object storage keys registered after render.
 */
public final class ObjectStoragePdfAttachmentPort implements PdfAttachmentPort {

    private final ObjectStorage objectStorage;
    private final Map<String, Registered> byNote = new ConcurrentHashMap<>();

    public ObjectStoragePdfAttachmentPort(ObjectStorage objectStorage) {
        this.objectStorage = Objects.requireNonNull(objectStorage, "objectStorage");
    }

    public void register(TenantId tenantId, UUID noteVersionId, UUID documentId, String storageKey) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(noteVersionId, "noteVersionId");
        Objects.requireNonNull(documentId, "documentId");
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey is required");
        }
        byNote.put(key(tenantId, noteVersionId), new Registered(documentId, storageKey.trim()));
    }

    @Override
    public PdfAttachmentDecision decide(TenantId tenantId, UUID noteVersionId, DeliveryPolicySnapshot policy) {
        if (!policy.shouldAttachPdf()) {
            String reason = policy.sensitiveMeeting()
                    ? "sensitive_meeting_uses_signed_portal_link"
                    : "pdf_attachment_disabled_by_policy";
            return PdfAttachmentDecision.deny(reason);
        }
        Registered registered = byNote.get(key(tenantId, noteVersionId));
        if (registered == null) {
            return PdfAttachmentDecision.deny("pdf_not_rendered");
        }
        return PdfAttachmentDecision.attach(registered.documentId(), registered.storageKey());
    }

    @Override
    public Optional<byte[]> loadPdfBytes(TenantId tenantId, PdfAttachmentDecision decision) {
        if (!decision.attach() || decision.objectKey() == null || decision.objectKey().isBlank()) {
            return Optional.empty();
        }
        try (InputStream in = objectStorage.get(decision.objectKey())) {
            return Optional.of(in.readAllBytes());
        } catch (ObjectStorageException | java.io.IOException ex) {
            return Optional.empty();
        }
    }

    private static String key(TenantId tenantId, UUID noteVersionId) {
        return tenantId.value() + "|" + noteVersionId;
    }

    private record Registered(UUID documentId, String storageKey) {
    }
}
