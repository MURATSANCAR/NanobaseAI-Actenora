package com.nanobaseai.actenora.sharedkernel.time;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Thin clock abstraction for deterministic tests. Not a business service.
 */
public final class InstantClock {

    private final Clock clock;

    public InstantClock(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static InstantClock systemUTC() {
        return new InstantClock(Clock.systemUTC());
    }

    public Instant now() {
        return clock.instant();
    }
}
