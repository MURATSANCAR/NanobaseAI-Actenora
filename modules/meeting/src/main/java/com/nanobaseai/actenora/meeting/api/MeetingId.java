package com.nanobaseai.actenora.meeting.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Opaque meeting reference published for other modules.
 */
public record MeetingId(UUID value) {

    public MeetingId {
        Objects.requireNonNull(value, "value");
    }

    public static MeetingId of(UUID value) {
        return new MeetingId(value);
    }
}
