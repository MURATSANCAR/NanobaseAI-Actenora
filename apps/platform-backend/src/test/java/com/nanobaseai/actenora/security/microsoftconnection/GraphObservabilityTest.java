package com.nanobaseai.actenora.security.microsoftconnection;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphObservabilityTest {

    @Test
    void opensCircuitAfterConsecutiveTransportFailuresAndClosesOnProbeSuccess() throws Exception {
        GraphObservability observability = new GraphObservability(
                new SimpleMeterRegistry(),
                2,
                Duration.ofMillis(50));

        observability.recordHttp(0, Duration.ofMillis(5));
        observability.recordHttp(500, Duration.ofMillis(5));
        assertEquals("OPEN", observability.circuitState());
        assertFalse(observability.allowRequest());

        Thread.sleep(60);
        assertTrue(observability.allowRequest());
        observability.recordHttp(200, Duration.ofMillis(5));
        assertEquals("CLOSED", observability.circuitState());
    }
}
