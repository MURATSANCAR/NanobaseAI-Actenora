package com.nanobaseai.actenora.template.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RenderedDocumentView(
        UUID id,
        UUID noteId,
        UUID renderJobId,
        String format,
        String storageKey,
        long sizeBytes,
        Instant createdAt
) {
    public RenderedDocumentView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(noteId, "noteId");
        Objects.requireNonNull(renderJobId, "renderJobId");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(storageKey, "storageKey");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
