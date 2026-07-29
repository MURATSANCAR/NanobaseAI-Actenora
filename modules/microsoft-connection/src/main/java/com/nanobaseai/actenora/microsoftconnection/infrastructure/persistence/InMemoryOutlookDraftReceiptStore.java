package com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence;

import com.nanobaseai.actenora.microsoftconnection.application.model.OutlookDraftResult;
import com.nanobaseai.actenora.microsoftconnection.application.port.OutlookDraftReceiptStore;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryOutlookDraftReceiptStore implements OutlookDraftReceiptStore {

    private final Map<Key, Receipt> receipts = new ConcurrentHashMap<>();

    @Override
    public Optional<Receipt> find(UUID tenantId, String idempotencyKey) {
        return Optional.ofNullable(receipts.get(new Key(tenantId, idempotencyKey)));
    }

    @Override
    public boolean tryClaim(UUID tenantId, String idempotencyKey, Instant claimedAt) {
        Key key = new Key(tenantId, idempotencyKey);
        Receipt pending = new Receipt(
                tenantId,
                idempotencyKey,
                Status.PENDING,
                null,
                null,
                claimedAt,
                null);
        return receipts.putIfAbsent(key, pending) == null;
    }

    @Override
    public void complete(
            UUID tenantId,
            String idempotencyKey,
            OutlookDraftResult result,
            Instant completedAt
    ) {
        Key key = new Key(tenantId, idempotencyKey);
        receipts.compute(key, (ignored, existing) -> {
            if (existing == null || existing.status() != Status.PENDING) {
                throw new IllegalStateException("Draft receipt claim is unavailable");
            }
            return new Receipt(
                    tenantId,
                    idempotencyKey,
                    Status.COMPLETED,
                    result.providerMessageId(),
                    result.webLink(),
                    existing.claimedAt(),
                    completedAt);
        });
    }

    @Override
    public void release(UUID tenantId, String idempotencyKey) {
        Key key = new Key(tenantId, idempotencyKey);
        receipts.computeIfPresent(key, (ignored, existing) ->
                existing.status() == Status.PENDING ? null : existing);
    }

    private record Key(UUID tenantId, String idempotencyKey) {
    }
}
