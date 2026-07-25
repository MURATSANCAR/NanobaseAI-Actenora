package com.nanobaseai.actenora.meetingintelligence.application.port;

import java.time.Instant;

public interface ClockPort {

    Instant now();
}
