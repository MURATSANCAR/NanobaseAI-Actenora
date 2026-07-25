package com.nanobaseai.actenora.operations.domain;

/**
 * Alert kinds raised by the Operations Center (FAZ 25).
 */
public enum AlertType {
    CERTIFICATE_EXPIRY,
    SLA_BREACH,
    DLQ_DEPTH,
    QUEUE_DEPTH,
    DEPLOYMENT_UNHEALTHY
}
