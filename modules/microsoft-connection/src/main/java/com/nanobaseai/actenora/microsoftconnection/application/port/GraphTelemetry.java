package com.nanobaseai.actenora.microsoftconnection.application.port;

import java.time.Duration;

public interface GraphTelemetry {

    void recordHttp(int statusCode, Duration duration);

    default boolean allowRequest() {
        return true;
    }

    default String circuitState() {
        return "CLOSED";
    }

    GraphTelemetry NOOP = new GraphTelemetry() {
        @Override
        public void recordHttp(int statusCode, Duration duration) {
        }
    };
}
