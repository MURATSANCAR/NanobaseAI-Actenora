package com.nanobaseai.actenora.meeting.application.port;

import java.time.Instant;

public interface ClockPort {

    Instant now();
}
