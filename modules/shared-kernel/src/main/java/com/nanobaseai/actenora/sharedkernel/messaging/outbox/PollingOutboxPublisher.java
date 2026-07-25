package com.nanobaseai.actenora.sharedkernel.messaging.outbox;

import com.nanobaseai.actenora.sharedkernel.messaging.DeadLetterEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.EventMessagingConfig;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxStatus;
import com.nanobaseai.actenora.sharedkernel.messaging.RetryClassification;
import com.nanobaseai.actenora.sharedkernel.messaging.RetryClassifier;
import com.nanobaseai.actenora.sharedkernel.messaging.port.DeadLetterStore;
import com.nanobaseai.actenora.sharedkernel.messaging.port.EventTransport;
import com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxRelay;
import com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.support.GracefulShutdownGate;
import com.nanobaseai.actenora.sharedkernel.messaging.support.TenantFairnessTracker;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Polling outbox publisher. CDC-ready: only this class talks to {@link EventTransport};
 * a log-based relay can replace polling while keeping the same stores and transport.
 * <p>
 * Crash after broker publish / before {@code published_at} commit: next poll re-publishes
 * (at-least-once). Consumers must be idempotent via inbox.
 */
public final class PollingOutboxPublisher implements OutboxRelay {

    private final OutboxStore outboxStore;
    private final DeadLetterStore deadLetterStore;
    private final EventTransport transport;
    private final EventMessagingConfig config;
    private final RetryClassifier classifier;
    private final InstantClock clock;
    private final TenantFairnessTracker fairness;
    private final GracefulShutdownGate gate;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;

    public PollingOutboxPublisher(
            OutboxStore outboxStore,
            DeadLetterStore deadLetterStore,
            EventTransport transport,
            EventMessagingConfig config,
            RetryClassifier classifier,
            InstantClock clock,
            TenantFairnessTracker fairness,
            GracefulShutdownGate gate
    ) {
        this.outboxStore = Objects.requireNonNull(outboxStore, "outboxStore");
        this.deadLetterStore = Objects.requireNonNull(deadLetterStore, "deadLetterStore");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.config = Objects.requireNonNull(config, "config");
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.fairness = Objects.requireNonNull(fairness, "fairness");
        this.gate = Objects.requireNonNull(gate, "gate");
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "actenora-outbox-relay");
            t.setDaemon(true);
            return t;
        });
        long period = Math.max(10L, config.pollInterval().toMillis());
        scheduler.scheduleWithFixedDelay(this::safePublishBatch, 0L, period, TimeUnit.MILLISECONDS);
    }

    private void safePublishBatch() {
        try {
            publishDueBatch();
        } catch (RuntimeException ignored) {
            // Keep polling; individual failures are recorded per event.
        }
    }

    @Override
    public int publishDueBatch() {
        if (!gate.tryEnter()) {
            return 0;
        }
        try {
            Instant now = clock.now();
            List<OutboxEvent> claimed = outboxStore.claimDue(now, config.publishBatchSize());
            int published = 0;
            for (OutboxEvent event : claimed) {
                if (publishOne(event, now)) {
                    published++;
                }
            }
            return published;
        } finally {
            gate.leave();
        }
    }

    private boolean publishOne(OutboxEvent event, Instant now) {
        event.markPublishing();
        outboxStore.save(event);
        EventEnvelope envelope = event.toEnvelope(config.producerName());
        try {
            transport.publish(envelope);
        } catch (EventTransport.TransportException ex) {
            handleFailure(event, now, RetryClassifier.Default.CODE_TRANSIENT, ex.getMessage());
            return false;
        } catch (RuntimeException ex) {
            RetryClassification classification = classifier.classify(ex);
            String code = ex instanceof com.nanobaseai.actenora.sharedkernel.error.ActenoraException ae
                    ? ae.code()
                    : classification.name();
            handleFailure(event, now, code, ex.getMessage());
            return false;
        }

        try {
            // Critical ordering: broker first, then mark published.
            // Crash between these lines → at-least-once redelivery on next poll (PUBLISHING reclaim).
            event.markPublished(now);
            outboxStore.save(event);
            fairness.recordPublished(event.tenantId());
            return true;
        } catch (RuntimeException ex) {
            // Leave status as PUBLISHING; do not classify as poison.
            return false;
        }
    }

    private void handleFailure(OutboxEvent event, Instant now, String failureCode, String detail) {
        RetryClassification classification = classifier.classify(failureCode);
        boolean exhausted = event.attemptCount() + 1 >= config.maxAttempts();
        if (classification == RetryClassification.TRANSIENT && !exhausted) {
            Instant next = now.plus(config.backoff().delayForAttempt(event.attemptCount()));
            event.scheduleRetry(next, failureCode);
            outboxStore.save(event);
            return;
        }
        String dlqCode = exhausted
                ? RetryClassifier.Default.CODE_MAX_ATTEMPTS
                : failureCode;
        event.markDeadLetter(dlqCode);
        outboxStore.save(event);
        deadLetterStore.append(new DeadLetterEvent(
                UUID.randomUUID(),
                DeadLetterEvent.DeadLetterSource.OUTBOX,
                event.id(),
                null,
                event.eventType(),
                event.eventVersion(),
                event.payloadJson(),
                dlqCode,
                detail,
                event.correlationId(),
                event.tenantId(),
                event.attemptCount(),
                now,
                null
        ));
        fairness.recordDeadLetter(event.tenantId());
    }

    @Override
    public void close() {
        gate.beginShutdown();
        running.set(false);
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                scheduler.shutdownNow();
            }
        }
        try {
            gate.awaitQuiescent(5_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get() && gate.isAccepting();
    }

    public long pendingCount() {
        return outboxStore.countByStatus(OutboxStatus.PENDING)
                + outboxStore.countByStatus(OutboxStatus.RETRY);
    }
}
