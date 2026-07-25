package com.nanobaseai.actenora.template.api;

import java.util.Objects;
import java.util.UUID;

public record MeetingTemplateId(UUID value) {

    public MeetingTemplateId {
        Objects.requireNonNull(value, "value");
    }

    public static MeetingTemplateId of(UUID value) {
        return new MeetingTemplateId(value);
    }

    public static MeetingTemplateId random() {
        return new MeetingTemplateId(UUID.randomUUID());
    }
}
