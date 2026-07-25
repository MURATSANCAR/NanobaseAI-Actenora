package com.nanobaseai.actenora.transcript.api;

import java.util.Objects;
import java.util.UUID;

public record TranscriptId(UUID value) {

    public TranscriptId {
        Objects.requireNonNull(value, "value");
    }

    public static TranscriptId of(UUID value) {
        return new TranscriptId(value);
    }
}
