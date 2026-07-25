package com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence;

import com.nanobaseai.actenora.meetingintelligence.application.port.ClockPort;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class FixedClockPort implements ClockPort {

    private final Clock clock;

    public FixedClockPort(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Instant now() {
        return clock.instant();
    }
}
