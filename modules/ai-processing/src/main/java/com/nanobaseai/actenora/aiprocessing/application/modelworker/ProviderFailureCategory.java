package com.nanobaseai.actenora.aiprocessing.application.modelworker;

/**
 * Safe, stable failure taxonomy for provider/worker errors.
 * Values are suitable for {@code failure_category} persistence — never include prompt text.
 */
public enum ProviderFailureCategory {
    CONNECT_TIMEOUT,
    READ_TIMEOUT,
    CONNECTION_FAILURE,
    MALFORMED_RESPONSE,
    CANCELLED,
    DRAINING,
    HEALTH_DEGRADED,
    MODEL_MISMATCH,
    INVALID_SERVED_MODEL,
    CONCURRENCY_LIMIT,
    STREAMING_NOT_SUPPORTED,
    PROVIDER_ERROR,
    UNKNOWN
}
