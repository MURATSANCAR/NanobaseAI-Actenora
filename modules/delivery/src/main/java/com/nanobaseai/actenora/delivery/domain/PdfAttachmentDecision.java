package com.nanobaseai.actenora.delivery.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * PDF attachment decision for a delivery. Sensitive meetings forbid attachments.
 */
public record PdfAttachmentDecision(
        boolean attach,
        UUID renderedDocumentId,
        String objectKey,
        String contentType,
        String denialReason
) {

    public PdfAttachmentDecision {
        Objects.requireNonNull(contentType, "contentType");
        if (attach) {
            Objects.requireNonNull(renderedDocumentId, "renderedDocumentId");
            Objects.requireNonNull(objectKey, "objectKey");
            if (denialReason != null && !denialReason.isBlank()) {
                throw new IllegalArgumentException("denialReason must be empty when attaching");
            }
        }
    }

    public static PdfAttachmentDecision attach(UUID renderedDocumentId, String objectKey) {
        return new PdfAttachmentDecision(
                true,
                renderedDocumentId,
                objectKey,
                "application/pdf",
                null
        );
    }

    public static PdfAttachmentDecision deny(String reason) {
        return new PdfAttachmentDecision(false, null, null, "application/pdf", reason);
    }

    public Optional<UUID> renderedDocumentIdOptional() {
        return Optional.ofNullable(renderedDocumentId);
    }

    public Optional<String> objectKeyOptional() {
        return Optional.ofNullable(objectKey);
    }
}
