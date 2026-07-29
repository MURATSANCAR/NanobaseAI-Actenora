package com.nanobaseai.actenora.microsoftconnection.infrastructure.mail;

import com.nanobaseai.actenora.microsoftconnection.application.model.OutlookDraftRequest;
import com.nanobaseai.actenora.microsoftconnection.application.model.OutlookDraftResult;
import com.nanobaseai.actenora.microsoftconnection.application.port.OutlookDraftGateway;
import com.nanobaseai.actenora.microsoftconnection.application.port.OutlookDraftReceiptStore;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.GraphApiException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cluster-safe idempotency barrier for Graph draft creation.
 */
public final class DurableOutlookDraftGateway implements OutlookDraftGateway {

    private static final String CODE_CREATION_PENDING = "OUTLOOK_DRAFT_CREATION_PENDING";

    private final OutlookDraftGateway delegate;
    private final OutlookDraftReceiptStore receipts;
    private final Supplier<Instant> clock;

    public DurableOutlookDraftGateway(
            OutlookDraftGateway delegate,
            OutlookDraftReceiptStore receipts
    ) {
        this(delegate, receipts, Instant::now);
    }

    DurableOutlookDraftGateway(
            OutlookDraftGateway delegate,
            OutlookDraftReceiptStore receipts,
            Supplier<Instant> clock
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.receipts = Objects.requireNonNull(receipts, "receipts");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public OutlookDraftResult create(UUID tenantId, OutlookDraftRequest request) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(request, "request");
        var existing = receipts.find(tenantId, request.idempotencyKey());
        if (existing.isPresent()) {
            if (existing.get().status() == OutlookDraftReceiptStore.Status.COMPLETED) {
                return existing.get().asResult();
            }
            throw pending();
        }
        if (!receipts.tryClaim(tenantId, request.idempotencyKey(), clock.get())) {
            return receipts.find(tenantId, request.idempotencyKey())
                    .filter(receipt -> receipt.status() == OutlookDraftReceiptStore.Status.COMPLETED)
                    .map(OutlookDraftReceiptStore.Receipt::asResult)
                    .orElseThrow(DurableOutlookDraftGateway::pending);
        }

        OutlookDraftResult created;
        try {
            created = delegate.create(tenantId, request);
        } catch (GraphApiException ex) {
            if (!ambiguous(ex)) {
                receipts.release(tenantId, request.idempotencyKey());
            }
            throw ex;
        } catch (RuntimeException ex) {
            throw GraphApiException.transport("Outlook draft creation outcome is ambiguous", ex);
        }

        try {
            receipts.complete(tenantId, request.idempotencyKey(), created, clock.get());
        } catch (RuntimeException ex) {
            throw GraphApiException.transport("Outlook draft receipt could not be completed", ex);
        }
        return created;
    }

    private static boolean ambiguous(GraphApiException failure) {
        return GraphApiException.CODE_SERVER_ERROR.equals(failure.code())
                || GraphApiException.CODE_TRANSPORT.equals(failure.code());
    }

    private static GraphApiException pending() {
        return new GraphApiException(
                CODE_CREATION_PENDING,
                CODE_CREATION_PENDING,
                409,
                null,
                false);
    }
}
