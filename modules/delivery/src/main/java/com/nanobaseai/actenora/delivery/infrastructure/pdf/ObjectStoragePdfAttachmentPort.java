package com.nanobaseai.actenora.delivery.infrastructure.pdf;

import com.nanobaseai.actenora.delivery.application.port.PdfAttachmentPort;
import com.nanobaseai.actenora.delivery.domain.DeliveryPolicySnapshot;
import com.nanobaseai.actenora.delivery.domain.PdfAttachmentDecision;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorage;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorageException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves PDF attachments from object storage. Registrations are kept in-memory for fast path
 * and mirrored to {@code delivery.pdf_attachments} when JDBC is available so worker restarts
 * still find rendered PDFs. Falls back to the branded fallback object key convention.
 */
public final class ObjectStoragePdfAttachmentPort implements PdfAttachmentPort {

    private final ObjectStorage objectStorage;
    private final Optional<JdbcTemplate> jdbc;
    private final Map<String, Registered> byNote = new ConcurrentHashMap<>();

    public ObjectStoragePdfAttachmentPort(ObjectStorage objectStorage) {
        this(objectStorage, null);
    }

    public ObjectStoragePdfAttachmentPort(ObjectStorage objectStorage, JdbcTemplate jdbcTemplate) {
        this.objectStorage = Objects.requireNonNull(objectStorage, "objectStorage");
        this.jdbc = Optional.ofNullable(jdbcTemplate);
    }

    public void register(TenantId tenantId, UUID noteVersionId, UUID documentId, String storageKey) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(noteVersionId, "noteVersionId");
        Objects.requireNonNull(documentId, "documentId");
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey is required");
        }
        Registered registered = new Registered(documentId, storageKey.trim());
        byNote.put(key(tenantId, noteVersionId), registered);
        jdbc.ifPresent(template -> template.update("""
                        INSERT INTO delivery.pdf_attachments (
                            tenant_id, note_version_id, document_id, storage_key, created_at
                        ) VALUES (?, ?, ?, ?, NOW())
                        ON CONFLICT (tenant_id, note_version_id) DO UPDATE SET
                            document_id = EXCLUDED.document_id,
                            storage_key = EXCLUDED.storage_key
                        """,
                tenantId.value(),
                noteVersionId,
                documentId,
                registered.storageKey()
        ));
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
            registered = loadPersisted(tenantId, noteVersionId).orElse(null);
        }
        if (registered == null) {
            registered = resolveFallback(tenantId, noteVersionId).orElse(null);
        }
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

    private Optional<Registered> loadPersisted(TenantId tenantId, UUID noteVersionId) {
        if (jdbc.isEmpty()) {
            return Optional.empty();
        }
        return jdbc.get().query("""
                        SELECT document_id, storage_key
                        FROM delivery.pdf_attachments
                        WHERE tenant_id = ? AND note_version_id = ?
                        """,
                (rs, rowNum) -> new Registered(
                        rs.getObject("document_id", UUID.class),
                        rs.getString("storage_key")
                ),
                tenantId.value(),
                noteVersionId
        ).stream().findFirst();
    }

    private Optional<Registered> resolveFallback(TenantId tenantId, UUID noteVersionId) {
        String fallbackKey = "tenants/" + tenantId.value() + "/notes/" + noteVersionId + "/fallback.pdf";
        if (!objectStorage.exists(fallbackKey)) {
            return Optional.empty();
        }
        UUID documentId = UUID.nameUUIDFromBytes(fallbackKey.getBytes(StandardCharsets.UTF_8));
        return Optional.of(new Registered(documentId, fallbackKey));
    }

    private static String key(TenantId tenantId, UUID noteVersionId) {
        return tenantId.value() + "|" + noteVersionId;
    }

    private record Registered(UUID documentId, String storageKey) {
    }
}
