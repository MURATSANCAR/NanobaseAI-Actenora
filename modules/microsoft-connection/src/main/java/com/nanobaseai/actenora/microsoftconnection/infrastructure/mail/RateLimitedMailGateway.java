package com.nanobaseai.actenora.microsoftconnection.infrastructure.mail;

import com.nanobaseai.actenora.microsoftconnection.application.model.MailSendRequest;
import com.nanobaseai.actenora.microsoftconnection.application.model.MailSendResult;
import com.nanobaseai.actenora.microsoftconnection.application.port.MailGateway;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.GraphApiException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Token-bucket style mail rate limiter wrapping a {@link MailGateway}.
 * Exceeding the per-tenant window throws {@link GraphApiException#rateLimited}.
 */
public final class RateLimitedMailGateway implements MailGateway {

    private final MailGateway delegate;
    private final int maxPerWindow;
    private final Duration window;
    private final Supplier<Instant> clock;
    private final Map<UUID, WindowCounter> counters = new ConcurrentHashMap<>();
    private final Map<String, String> idempotencyIndex = new ConcurrentHashMap<>();
    private final AtomicInteger rateLimitedCount = new AtomicInteger();

    public RateLimitedMailGateway(MailGateway delegate, int maxPerWindow, Duration window) {
        this(delegate, maxPerWindow, window, Instant::now);
    }

    public RateLimitedMailGateway(
            MailGateway delegate,
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
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public MailSendResult send(UUID tenantId, MailSendRequest request) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(request, "request");

        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            String key = tenantId + "::" + request.idempotencyKey();
            String existing = idempotencyIndex.get(key);
            if (existing != null) {
                return MailSendResult.accepted(existing);
            }
        }

        Instant now = clock.get();
        WindowCounter counter = counters.computeIfAbsent(tenantId, id -> new WindowCounter(now));
        synchronized (counter) {
            if (Duration.between(counter.windowStart, now).compareTo(window) >= 0) {
                counter.windowStart = now;
                counter.count = 0;
            }
            if (counter.count >= maxPerWindow) {
                rateLimitedCount.incrementAndGet();
                throw GraphApiException.rateLimited(
                        "Mail rate limit exceeded for tenant=" + tenantId,
                        window
                );
            }
            counter.count++;
        }

        MailSendResult result = delegate.send(tenantId, request);
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            idempotencyIndex.putIfAbsent(tenantId + "::" + request.idempotencyKey(), result.providerMessageId());
        }
        return result;
    }

    public int rateLimitedCount() {
        return rateLimitedCount.get();
    }

    private static final class WindowCounter {
        private Instant windowStart;
        private int count;

        private WindowCounter(Instant windowStart) {
            this.windowStart = windowStart;
        }
    }
}
