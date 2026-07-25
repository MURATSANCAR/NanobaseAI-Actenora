package com.nanobaseai.actenora.meetingintelligence.api;

import java.util.Objects;
import java.util.UUID;

public record MeetingNoteId(UUID value) {

    public MeetingNoteId {
        Objects.requireNonNull(value, "value");
    }

    public static MeetingNoteId of(UUID value) {
        return new MeetingNoteId(value);
    }
}
