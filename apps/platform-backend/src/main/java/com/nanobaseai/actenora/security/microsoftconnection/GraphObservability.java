package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.microsoftconnection.application.port.GraphTelemetry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(name = "actenora.microsoft-graph.enabled", havingValue = "true")
public final class GraphObservability implements GraphTelemetry {

    private final MeterRegistry registry;
    private final int failureThreshold;
    private final Duration openDuration;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicReference<Instant> openedAt = new AtomicReference<>();
    private final AtomicBoolean halfOpenProbe = new AtomicBoolean();
    private final AtomicReference<Instant> lastWebhookAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastWebhookRejectionAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastReconcileSuccessAt = new AtomicReference<>();
    private final AtomicLong expiringSubscriptions = new AtomicLong();
    private final AtomicLong transcriptPending = new AtomicLong();
    private final AtomicLong oldestTranscriptPendingSeconds = new AtomicLong();

    public GraphObservability(
            MeterRegistry registry,
            @Value("${actenora.microsoft-graph.circuit-breaker.failure-threshold:10}") int failureThreshold,
            @Value("${actenora.microsoft-graph.circuit-breaker.open-duration:PT30S}") Duration openDuration
    ) {
        this.registry = Objects.requireNonNull(registry);
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be positive");
        }
        this.failureThreshold = failureThreshold;
        this.openDuration = Objects.requireNonNull(openDuration);
        registry.gauge("actenora.graph.circuit.open", this, value -> value.state.get() == State.OPEN ? 1 : 0);
        registry.gauge("actenora.graph.subscriptions.expiring", expiringSubscriptions);
        registry.gauge("actenora.graph.transcript.pending", transcriptPending);
        registry.gauge("actenora.graph.transcript.oldest.pending.seconds", oldestTranscriptPendingSeconds);
    }

    @Override
    public boolean allowRequest() {
        State current = state.get();
        if (current == State.CLOSED) {
            return true;
        }
        if (current == State.OPEN) {
            Instant opened = openedAt.get();
            if (opened == null || Instant.now().isBefore(opened.plus(openDuration))) {
                registry.counter("actenora.graph.circuit.rejected").increment();
                return false;
            }
            state.compareAndSet(State.OPEN, State.HALF_OPEN);
        }
        boolean allowed = halfOpenProbe.compareAndSet(false, true);
        if (!allowed) {
            registry.counter("actenora.graph.circuit.rejected").increment();
        }
        return allowed;
    }

    @Override
    public void recordHttp(int statusCode, Duration duration) {
        String outcome = statusCode == 0 ? "transport" : Integer.toString(statusCode / 100) + "xx";
        Counter.builder("actenora.graph.http.requests")
                .tag("outcome", outcome)
                .tag("status", Integer.toString(statusCode))
                .register(registry)
                .increment();
        Timer.builder("actenora.graph.http.duration")
                .tag("outcome", outcome)
                .tag("status", Integer.toString(statusCode))
                .register(registry)
                .record(duration.toNanos(), TimeUnit.NANOSECONDS);

        boolean breakerFailure = statusCode == 0 || statusCode == 429 || statusCode >= 500;
        if (breakerFailure) {
            if (state.get() == State.HALF_OPEN || consecutiveFailures.incrementAndGet() >= failureThreshold) {
                state.set(State.OPEN);
                openedAt.set(Instant.now());
                halfOpenProbe.set(false);
                registry.counter("actenora.graph.circuit.transitions", "state", "OPEN").increment();
            }
        } else {
            consecutiveFailures.set(0);
            if (state.getAndSet(State.CLOSED) != State.CLOSED) {
                registry.counter("actenora.graph.circuit.transitions", "state", "CLOSED").increment();
            }
            halfOpenProbe.set(false);
        }
    }

    @Override
    public String circuitState() {
        return state.get().name();
    }

    public void recordWebhook(int received, int processed, int duplicates, int rejected) {
        lastWebhookAt.set(Instant.now());
        registry.counter("actenora.graph.webhook.notifications", "outcome", "received").increment(received);
        registry.counter("actenora.graph.webhook.notifications", "outcome", "processed").increment(processed);
        registry.counter("actenora.graph.webhook.notifications", "outcome", "duplicate").increment(duplicates);
        registry.counter("actenora.graph.webhook.notifications", "outcome", "rejected").increment(rejected);
        if (rejected > 0) {
            lastWebhookRejectionAt.set(Instant.now());
        }
    }

    public void recordTenantUnmapped() {
        registry.counter("actenora.graph.tenant.unmapped").increment();
    }

    public void recordLifecycle(String lifecycleEvent) {
        registry.counter(
                "actenora.graph.subscription.lifecycle",
                "event",
                lifecycleEvent == null ? "unknown" : lifecycleEvent).increment();
    }

    public void recordReconciliation(boolean success) {
        registry.counter("actenora.graph.reconciliation", "outcome", success ? "success" : "failure").increment();
        if (success) {
            lastReconcileSuccessAt.set(Instant.now());
        }
    }

    public void recordTranscriptPoll(String outcome) {
        registry.counter("actenora.graph.transcript.poll", "outcome", outcome).increment();
    }

    public void updateExpiringSubscriptions(long expiring) {
        expiringSubscriptions.set(Math.max(0L, expiring));
    }

    public void updateTranscriptGauges(long pending, long oldestPendingSeconds) {
        transcriptPending.set(Math.max(0L, pending));
        oldestTranscriptPendingSeconds.set(Math.max(0L, oldestPendingSeconds));
    }

    public String webhookStatus() {
        Instant rejection = lastWebhookRejectionAt.get();
        if (rejection != null && rejection.isAfter(Instant.now().minus(Duration.ofMinutes(15)))) {
            return "degraded";
        }
        return lastWebhookAt.get() == null ? "pending" : "active";
    }

    public Instant lastReconcileSuccessAt() {
        return lastReconcileSuccessAt.get();
    }

    private enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }
}
