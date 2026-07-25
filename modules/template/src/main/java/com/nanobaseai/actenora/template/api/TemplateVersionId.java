package com.nanobaseai.actenora.template.api;

import java.util.Objects;
import java.util.UUID;

public record TemplateVersionId(UUID value) {

    public TemplateVersionId {
        Objects.requireNonNull(value, "value");
    }

    public static TemplateVersionId of(UUID value) {
        return new TemplateVersionId(value);
    }

    public static TemplateVersionId random() {
        return new TemplateVersionId(UUID.randomUUID());
    }
}
