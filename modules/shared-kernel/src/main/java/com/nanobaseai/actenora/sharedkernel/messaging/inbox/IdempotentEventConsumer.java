package com.nanobaseai.actenora.sharedkernel.messaging.inbox;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import com.nanobaseai.actenora.sharedkernel.messaging.CorrelationContext;
import com.nanobaseai.actenora.sharedkernel.messaging.DeadLetterEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.EventMessagingConfig;
import com.nanobaseai.actenora.sharedkernel.messaging.EventSchemaValidator;
import com.nanobaseai.actenora.sharedkernel.messaging.InboxEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.InboxStatus;
import com.nanobaseai.actenora.sharedkernel.messaging.RetryClassification;
import com.nanobaseai.actenora.sharedkernel.messaging.RetryClassifier;
import com.nanobaseai.actenora.sharedkernel.messaging.port.DeadLetterStore;
import com.nanobaseai.actenora.sharedkernel.messaging.port.InboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.support.GracefulShutdownGate;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Idempotent consumer pipeline: validate → inbox claim → handle → commit status.
 * <p>
 * Crash before inbox commit after side effects: handler must itself be idempotent;
 * duplicate delivery hits {@link InboxStore#claim} and is skipped when already PROCESSED.
 */
public final class IdempotentEventConsumer implements AutoCloseable {

    private final String consumerName;
    private final InboxStore inboxStore;
    private final DeadLetterStore deadLetterStore;
    private final EventSchemaValidator validator;
    private final EventMessagingConfig config;
    private final RetryClassifier classifier;
    private final InstantClock clock;
    private final GracefulShutdownGate gate;
    private final Semaphore concurrency;
    private final ExecutorService executor;
    private final ConcurrentHashMap<UUID, AtomicInteger> attemptByEvent = new ConcurrentHashMap<>();

    public IdempotentEventConsumer(
            String consumerName,
            InboxStore inboxStore,
            DeadLetterStore deadLetterStore,
            EventSchemaValidator validator,
            EventMessagingConfig config,
            RetryClassifier classifier,
            InstantClock clock,
            GracefulShutdownGate gate
    ) {
        this.consumerName = Objects.requireNonNull(consumerName, "consumerName");
        this.inboxStore = Objects.requireNonNull(inboxStore, "inboxStore");
        this.deadLetterStore = Objects.requireNonNull(deadLetterStore, "deadLetterStore");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.config = Objects.requireNonNull(config, "config");
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.concurrency = new Semaphore(config.consumerConcurrency());
        this.executor = Executors.newFixedThreadPool(config.consumerConcurrency(), r -> {
            Thread t = new Thread(r, "actenora-consumer-" + consumerName);
            t.setDaemon(true);
            return t;
        });
    }

    public String consumerName() {
        return consumerName;
    }

    public int consumerConcurrency() {
        return config.consumerConcurrency();
    }

    /**
     * Synchronously consume one envelope (tests / single-threaded relays).
     */
    public ConsumeResult consume(EventEnvelope envelope, Consumer<EventEnvelope> handler) {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(handler, "handler");
        if (!gate.tryEnter()) {
            return ConsumeResult.rejectedShutdown();
        }
        try {
            return doConsume(envelope, handler);
        } finally {
            gate.leave();
        }
    }

    /**
     * Async consume respecting configured concurrency.
     */
    public void consumeAsync(EventEnvelope envelope, Consumer<EventEnvelope> handler) {
        concurrency.acquireUninterruptibly();
        executor.execute(() -> {
            try {
                consume(envelope, handler);
            } finally {
                concurrency.release();
            }
        });
    }

    private ConsumeResult doConsume(EventEnvelope envelope, Consumer<EventEnvelope> handler) {
        Instant now = clock.now();
        try {
            validator.validateForConsume(envelope);
        } catch (ActenoraException ex) {
            return deadLetterReject(envelope, now, ex.code(), ex.getMessage());
        }

        InboxStore.ClaimResult claim = inboxStore.claim(InboxEvent.received(consumerName, envelope.eventId(), now));
        InboxEvent inbox = claim.event();
        if (claim.outcome() == InboxStore.ClaimOutcome.DUPLICATE) {
            if (inbox.isTerminalSuccess() || inbox.status() == InboxStatus.DEAD_LETTER) {
                return ConsumeResult.duplicate(inbox.status());
            }
            // Prior crash before commit: allow retry of non-terminal rows.
        }

        inbox.markProcessing();
        inboxStore.save(inbox);

        CorrelationContext context = CorrelationContext.continueFrom(
                envelope.correlationId(),
                envelope.eventId(),
                envelope.traceId());
        try (CorrelationContext.Scope ignored = CorrelationContext.open(context)) {
            try {
                handler.accept(envelope);
            } catch (RuntimeException ex) {
                return handleHandlerFailure(envelope, inbox, ex);
            }
            // Crash before this save ⇒ inbox stays PROCESSING; redelivery retries.
            inbox.markProcessed(clock.now());
            inboxStore.save(inbox);
            return ConsumeResult.processed();
        }
    }

    private ConsumeResult handleHandlerFailure(EventEnvelope envelope, InboxEvent inbox, RuntimeException ex) {
        RetryClassification classification = classifier.classify(ex);
        String code = ex instanceof ActenoraException ae ? ae.code() : classification.name();
        int attempts = attemptByEvent
                .computeIfAbsent(envelope.eventId(), ignored -> new AtomicInteger())
                .incrementAndGet();
        boolean poisonOrReject = classification != RetryClassification.TRANSIENT;
        boolean exhausted = attempts >= config.maxAttempts();

        if (!poisonOrReject && !exhausted) {
            inbox.markFailed(code);
            inboxStore.save(inbox);
            return ConsumeResult.retry(code);
        }

        String dlqCode = exhausted && !poisonOrReject
                ? RetryClassifier.Default.CODE_MAX_ATTEMPTS
                : code;
        inbox.markDeadLetter(dlqCode);
        inboxStore.save(inbox);
        Instant now = clock.now();
        deadLetterStore.append(new DeadLetterEvent(
                UUID.randomUUID(),
                DeadLetterEvent.DeadLetterSource.INBOX,
                envelope.eventId(),
                consumerName,
                envelope.eventType(),
                envelope.eventVersion(),
                envelope.payloadJson(),
                dlqCode,
                ex.getMessage(),
                envelope.correlationId(),
                envelope.tenantId(),
                attempts,
                now,
                null
        ));
        attemptByEvent.remove(envelope.eventId());
        return ConsumeResult.deadLetter(dlqCode);
    }

    private ConsumeResult deadLetterReject(
            EventEnvelope envelope,
            Instant now,
            String code,
            String detail
    ) {
        InboxStore.ClaimResult claim = inboxStore.claim(InboxEvent.received(consumerName, envelope.eventId(), now));
        InboxEvent inbox = claim.event();
        if (claim.outcome() == InboxStore.ClaimOutcome.DUPLICATE && inbox.status() == InboxStatus.DEAD_LETTER) {
            return ConsumeResult.duplicate(InboxStatus.DEAD_LETTER);
        }
        inbox.markDeadLetter(code);
        inboxStore.save(inbox);
        deadLetterStore.append(new DeadLetterEvent(
                UUID.randomUUID(),
                DeadLetterEvent.DeadLetterSource.INBOX,
                envelope.eventId(),
                consumerName,
                envelope.eventType(),
                envelope.eventVersion(),
                envelope.payloadJson(),
                code,
                detail,
                envelope.correlationId(),
                envelope.tenantId(),
                1,
                now,
                null
        ));
        return ConsumeResult.deadLetter(code);
    }

    @Override
    public void close() {
        gate.beginShutdown();
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        try {
            gate.awaitQuiescent(5_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record ConsumeResult(Outcome outcome, InboxStatus inboxStatus, String failureCode) {
        public static ConsumeResult processed() {
            return new ConsumeResult(Outcome.PROCESSED, InboxStatus.PROCESSED, null);
        }

        public static ConsumeResult duplicate(InboxStatus status) {
            return new ConsumeResult(Outcome.DUPLICATE, status, null);
        }

        public static ConsumeResult retry(String code) {
            return new ConsumeResult(Outcome.RETRY, InboxStatus.FAILED, code);
        }

        public static ConsumeResult deadLetter(String code) {
            return new ConsumeResult(Outcome.DEAD_LETTER, InboxStatus.DEAD_LETTER, code);
        }

        public static ConsumeResult rejectedShutdown() {
            return new ConsumeResult(Outcome.REJECTED_SHUTDOWN, null, "SHUTTING_DOWN");
        }
    }

    public enum Outcome {
        PROCESSED,
        DUPLICATE,
        RETRY,
        DEAD_LETTER,
        REJECTED_SHUTDOWN
    }
}
