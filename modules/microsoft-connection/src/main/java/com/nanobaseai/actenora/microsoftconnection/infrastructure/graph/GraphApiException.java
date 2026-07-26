package com.nanobaseai.actenora.microsoftconnection.infrastructure.graph;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

import java.time.Duration;
import java.util.Optional;

/**
 * Microsoft Graph / identity HTTP failure with classification codes used by retry policy.
 */
public class GraphApiException extends ActenoraException {

    public static final String CODE_TOKEN_FAILURE = "GRAPH_TOKEN_FAILURE";
    public static final String CODE_UNAUTHORIZED = "GRAPH_UNAUTHORIZED";
    public static final String CODE_CONFIGURATION = "GRAPH_CONFIGURATION_ERROR";
    public static final String CODE_NOT_FOUND = "GRAPH_NOT_FOUND";
    public static final String CODE_RATE_LIMITED = "GRAPH_RATE_LIMITED";
    public static final String CODE_SERVER_ERROR = "GRAPH_SERVER_ERROR";
    public static final String CODE_TRANSPORT = "GRAPH_TRANSPORT_ERROR";
    public static final String CODE_CIRCUIT_OPEN = "GRAPH_CIRCUIT_OPEN";

    private final int statusCode;
    private final Duration retryAfter;
    private final boolean retryable;

    public GraphApiException(
            String code,
            String message,
            int statusCode,
            Duration retryAfter,
            boolean retryable
    ) {
        super(code, message);
        this.statusCode = statusCode;
        this.retryAfter = retryAfter;
        this.retryable = retryable;
    }

    public GraphApiException(
            String code,
            String message,
            int statusCode,
            Duration retryAfter,
            boolean retryable,
            Throwable cause
    ) {
        super(code, message, cause);
        this.statusCode = statusCode;
        this.retryAfter = retryAfter;
        this.retryable = retryable;
    }

    public int statusCode() {
        return statusCode;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }

    public boolean retryable() {
        return retryable;
    }

    public static GraphApiException tokenFailure(String message) {
        return new GraphApiException(CODE_TOKEN_FAILURE, message, 0, null, true);
    }

    public static GraphApiException unauthorized(String message) {
        return new GraphApiException(CODE_UNAUTHORIZED, message, 401, null, true);
    }

    public static GraphApiException configuration(String message) {
        return new GraphApiException(CODE_CONFIGURATION, message, 403, null, false);
    }

    public static GraphApiException notFound(String message) {
        return new GraphApiException(CODE_NOT_FOUND, message, 404, null, true);
    }

    public static GraphApiException rateLimited(String message, Duration retryAfter) {
        return new GraphApiException(CODE_RATE_LIMITED, message, 429, retryAfter, true);
    }

    public static GraphApiException serverError(int status, String message) {
        return new GraphApiException(CODE_SERVER_ERROR, message, status, null, true);
    }

    public static GraphApiException transport(String message, Throwable cause) {
        return new GraphApiException(CODE_TRANSPORT, message, 0, null, true, cause);
    }

    public static GraphApiException circuitOpen() {
        return new GraphApiException(
                CODE_CIRCUIT_OPEN,
                "Microsoft Graph circuit breaker is open",
                0,
                Duration.ofSeconds(30),
                true);
    }
}
