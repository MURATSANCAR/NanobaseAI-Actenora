package com.nanobaseai.actenora.microsoftconnection.application.port;

import com.nanobaseai.actenora.microsoftconnection.application.model.OutlookDraftResult;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable idempotency receipt for non-idempotent Graph draft creation.
 */
public interface OutlookDraftReceiptStore {

    Optional<Receipt> find(UUID tenantId, String idempotencyKey);

    boolean tryClaim(UUID tenantId, String idempotencyKey, Instant claimedAt);

    void complete(
            UUID tenantId,
            String idempotencyKey,
            OutlookDraftResult result,
            Instant completedAt
    );

    void release(UUID tenantId, String idempotencyKey);

    enum Status {
        PENDING,
        COMPLETED
    }

    record Receipt(
            UUID tenantId,
            String idempotencyKey,
            Status status,
            String providerMessageId,
            String webLink,
            Instant claimedAt,
            Instant completedAt
    ) {
        public OutlookDraftResult asResult() {
            if (status != Status.COMPLETED || providerMessageId == null) {
                throw new IllegalStateException("Draft receipt is not completed");
            }
            return new OutlookDraftResult(providerMessageId, webLink, true);
        }
    }
}
