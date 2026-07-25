package com.nanobaseai.actenora.template.api;

import java.util.Objects;
import java.util.UUID;

public record RenderJobId(UUID value) {

    public RenderJobId {
        Objects.requireNonNull(value, "value");
    }

    public static RenderJobId of(UUID value) {
        return new RenderJobId(value);
    }

    public static RenderJobId random() {
        return new RenderJobId(UUID.randomUUID());
    }
}
