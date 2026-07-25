package com.nanobaseai.actenora.microsoftconnection.infrastructure.graph;

import java.time.Duration;

/**
 * Sleep abstraction so WireMock tests can avoid real waits.
 */
@FunctionalInterface
public interface GraphSleeper {

    void sleep(Duration duration) throws InterruptedException;

    GraphSleeper NOOP = duration -> {
    };

    GraphSleeper THREAD = duration -> {
        if (duration != null && !duration.isNegative() && !duration.isZero()) {
            Thread.sleep(duration.toMillis());
        }
    };
}
