package com.nanobaseai.actenora.microsoftconnection.infrastructure.mail;

import com.nanobaseai.actenora.microsoftconnection.application.model.OutlookDraftRequest;
import com.nanobaseai.actenora.microsoftconnection.application.model.OutlookDraftResult;
import com.nanobaseai.actenora.microsoftconnection.application.port.OutlookDraftGateway;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.GraphApiException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * Tenant-scoped rate limiting and process-safe retry deduplication for draft creation.
 */
public final class RateLimitedOutlookDraftGateway implements OutlookDraftGateway {

    private final OutlookDraftGateway delegate;
    private final int maxPerWindow;
    private final Duration window;
    private final Supplier<Instant> clock;
    private final Map<UUID, WindowCounter> counters = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<OutlookDraftResult>> idempotencyIndex =
            new ConcurrentHashMap<>();

    public RateLimitedOutlookDraftGateway(
            OutlookDraftGateway delegate,
            int maxPerWindow,
            Duration window
    ) {
        this(delegate, maxPerWindow, window, Instant::now);
    }

    RateLimitedOutlookDraftGateway(
            OutlookDraftGateway delegate,
            int maxPerWindow,
            Duration window,
            Supplier<Instant> clock
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (maxPerWindow < 1) {
            throw new IllegalArgumentException("maxPerWindow must be >= 1");
        }
        this.maxPerWindow = maxPerWindow;
        this.window = Objects.requireNonNull(window, "window");
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public OutlookDraftResult create(UUID tenantId, OutlookDraftRequest request) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(request, "request");
        String key = tenantId + "::" + request.idempotencyKey();
        CompletableFuture<OutlookDraftResult> claim = new CompletableFuture<>();
        CompletableFuture<OutlookDraftResult> existing = idempotencyIndex.putIfAbsent(key, claim);
        if (existing != null) {
            return await(existing).asReused();
        }

        try {
            acquireTenantPermit(tenantId);
            OutlookDraftResult created = delegate.create(tenantId, request);
            claim.complete(created);
            return created;
        } catch (RuntimeException | Error ex) {
            claim.completeExceptionally(ex);
            if (!ambiguousMutationOutcome(ex)) {
                idempotencyIndex.remove(key, claim);
            }
            throw ex;
        }
    }

    private static boolean ambiguousMutationOutcome(Throwable failure) {
        if (!(failure instanceof GraphApiException graph)) {
            return false;
        }
        return GraphApiException.CODE_SERVER_ERROR.equals(graph.code())
                || GraphApiException.CODE_TRANSPORT.equals(graph.code());
    }

    private void acquireTenantPermit(UUID tenantId) {
        Instant now = clock.get();
        WindowCounter counter = counters.computeIfAbsent(tenantId, ignored -> new WindowCounter(now));
        synchronized (counter) {
            if (Duration.between(counter.windowStart, now).compareTo(window) >= 0) {
                counter.windowStart = now;
                counter.count = 0;
            }
            if (counter.count >= maxPerWindow) {
                throw GraphApiException.rateLimited(
                        "Outlook draft rate limit exceeded for tenant=" + tenantId,
                        window);
            }
            counter.count++;
        }
    }

    private static OutlookDraftResult await(CompletableFuture<OutlookDraftResult> future) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (ex.getCause() instanceof Error error) {
                throw error;
            }
            throw ex;
        }
    }

    private static final class WindowCounter {
        private Instant windowStart;
        private int count;

        private WindowCounter(Instant windowStart) {
            this.windowStart = windowStart;
        }
    }
}
