package com.nanobaseai.actenora.operations.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Raised ops alert (FAZ 25).
 */
public record OpsAlert(
        UUID id,
        AlertType type,
        AlertSeverity severity,
        String title,
        String detail,
        Instant raisedAt,
        boolean acknowledged
) {
    public OpsAlert {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(raisedAt, "raisedAt");
    }

    public OpsAlert acknowledge() {
        return new OpsAlert(id, type, severity, title, detail, raisedAt, true);
    }
}
