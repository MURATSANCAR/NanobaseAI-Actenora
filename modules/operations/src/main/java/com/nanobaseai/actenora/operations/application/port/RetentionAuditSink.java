package com.nanobaseai.actenora.operations.application.port;

import com.nanobaseai.actenora.operations.domain.retention.RetentionCandidate;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

/** Sink for retention/legal-hold audit events (never includes content payloads). */
public interface RetentionAuditSink {

    void recordDeletion(RetentionCandidate candidate, String correlationId);

    void recordLegalHoldBlocked(RetentionCandidate candidate, String correlationId);

    void recordLegalHoldPlaced(
            TenantId tenantId,
            String resourceType,
            String resourceId,
            String reason,
            String correlationId
    );
}
