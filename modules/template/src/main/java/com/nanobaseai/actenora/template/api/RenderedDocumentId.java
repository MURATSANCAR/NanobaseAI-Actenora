package com.nanobaseai.actenora.template.api;

import java.util.Objects;
import java.util.UUID;

public record RenderedDocumentId(UUID value) {

    public RenderedDocumentId {
        Objects.requireNonNull(value, "value");
    }

    public static RenderedDocumentId of(UUID value) {
        return new RenderedDocumentId(value);
    }

    public static RenderedDocumentId random() {
        return new RenderedDocumentId(UUID.randomUUID());
    }
}
