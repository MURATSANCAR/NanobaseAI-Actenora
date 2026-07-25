package com.nanobaseai.actenora.meeting.domain.collaboration;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Marker offsets are always derived from server time relative to the meeting anchor.
 * Client-reported click times are never trusted for offset calculation.
 */
public final class MarkerOffsetCalculator {

    private MarkerOffsetCalculator() {
    }

    /**
     * @param meetingAnchor actual start if known, otherwise scheduled start
     * @param serverNow     clock from the server (never client time)
     * @return milliseconds from anchor; may be negative if marked before start
     */
    public static long offsetMs(Instant meetingAnchor, Instant serverNow) {
        Objects.requireNonNull(meetingAnchor, "meetingAnchor");
        Objects.requireNonNull(serverNow, "serverNow");
        return Duration.between(meetingAnchor, serverNow).toMillis();
    }

    public static Instant resolveAnchor(Instant actualStartAt, Instant scheduledStartAt) {
        if (actualStartAt != null) {
            return actualStartAt;
        }
        return Objects.requireNonNull(scheduledStartAt, "scheduledStartAt");
    }
}
