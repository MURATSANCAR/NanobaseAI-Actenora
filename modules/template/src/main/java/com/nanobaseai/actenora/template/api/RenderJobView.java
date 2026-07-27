package com.nanobaseai.actenora.template.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record RenderJobView(
        UUID id,
        UUID noteId,
        String format,
        String status,
        Optional<UUID> renderedDocumentId,
        Instant createdAt,
        Instant updatedAt,
        Optional<String> lastError
) {
    public RenderJobView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(noteId, "noteId");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(renderedDocumentId, "renderedDocumentId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(lastError, "lastError");
    }
}
