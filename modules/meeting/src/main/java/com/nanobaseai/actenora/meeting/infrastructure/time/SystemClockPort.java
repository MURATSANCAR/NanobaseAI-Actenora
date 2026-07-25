package com.nanobaseai.actenora.meeting.infrastructure.time;

import com.nanobaseai.actenora.meeting.application.port.ClockPort;

import java.time.Instant;

public final class SystemClockPort implements ClockPort {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
