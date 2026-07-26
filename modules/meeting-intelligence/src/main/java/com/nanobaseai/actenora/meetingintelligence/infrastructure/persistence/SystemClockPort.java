package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.ClockPort;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Production clock for Meeting Intelligence. */
public final class SystemClockPort implements ClockPort {

    private final Clock clock;

    public SystemClockPort() {
        this(Clock.systemUTC());
    }

    public SystemClockPort(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Instant now() {
        return clock.instant();
    }
}
