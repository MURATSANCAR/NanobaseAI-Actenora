package com.nanobaseai.actenora.sharedkernel.messaging.replay;

import com.nanobaseai.actenora.sharedkernel.messaging.DeadLetterEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.InboxEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxStatus;
import com.nanobaseai.actenora.sharedkernel.messaging.port.DeadLetterStore;
import com.nanobaseai.actenora.sharedkernel.messaging.port.InboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxStore;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Safe event replay for operators.
 * <ul>
 *   <li>Requires explicit reason (auditability).</li>
 *   <li>Dry-run mode inspects without mutating.</li>
 *   <li>Outbox replay resets to PENDING with same event id (idempotent transport + inbox).</li>
 *   <li>Inbox replay only allowed from DEAD_LETTER / FAILED — never duplicates PROCESSED success.</li>
 * </ul>
 */
public final class EventReplayer {

    private final OutboxStore outboxStore;
    private final InboxStore inboxStore;
    private final DeadLetterStore deadLetterStore;
    private final InstantClock clock;

    public EventReplayer(
            OutboxStore outboxStore,
            InboxStore inboxStore,
            DeadLetterStore deadLetterStore,
            InstantClock clock
    ) {
        this.outboxStore = Objects.requireNonNull(outboxStore, "outboxStore");
        this.inboxStore = Objects.requireNonNull(inboxStore, "inboxStore");
        this.deadLetterStore = Objects.requireNonNull(deadLetterStore, "deadLetterStore");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ReplayResult replayOutbox(UUID eventId, ReplayRequest request) {
        validate(request);
        OutboxEvent event = outboxStore.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Outbox event not found: " + eventId));
        if (event.status() != OutboxStatus.DEAD_LETTER && event.status() != OutboxStatus.PUBLISHED) {
            return ReplayResult.rejected("Only DEAD_LETTER or PUBLISHED outbox rows may be replayed");
        }
        if (request.dryRun()) {
            return ReplayResult.dryRunOk(event.status().name());
        }
        event.resetForReplay(clock.now());
        outboxStore.save(event);
        markDlqReplayed(eventId);
        return ReplayResult.applied(OutboxStatus.PENDING.name());
    }

    public ReplayResult replayInbox(String consumerName, UUID eventId, ReplayRequest request) {
        validate(request);
        Objects.requireNonNull(consumerName, "consumerName");
        InboxEvent event = inboxStore.find(consumerName, eventId)
                .orElseThrow(() -> new IllegalArgumentException("Inbox event not found"));
        return switch (event.status()) {
            case PROCESSED -> ReplayResult.rejected("Refusing to replay successfully PROCESSED inbox row");
            case PROCESSING, RECEIVED -> ReplayResult.rejected("Event is already in-flight: " + event.status());
            case FAILED, DEAD_LETTER -> {
                if (request.dryRun()) {
                    yield ReplayResult.dryRunOk(event.status().name());
                }
                event.resetForReplay();
                inboxStore.save(event);
                markDlqReplayed(eventId);
                yield ReplayResult.applied(event.status().name());
            }
        };
    }

    private void markDlqReplayed(UUID eventId) {
        Optional<DeadLetterEvent> dlq = deadLetterStore.findByEventId(eventId);
        dlq.ifPresent(event -> deadLetterStore.save(event.markReplayed(clock.now())));
    }

    private static void validate(ReplayRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.reason() == null || request.reason().isBlank()) {
            throw new IllegalArgumentException("Replay reason is required");
        }
        if (request.operator() == null || request.operator().isBlank()) {
            throw new IllegalArgumentException("Replay operator is required");
        }
    }

    public record ReplayRequest(String operator, String reason, boolean dryRun) {
        public static ReplayRequest of(String operator, String reason) {
            return new ReplayRequest(operator, reason, false);
        }

        public static ReplayRequest dryRun(String operator, String reason) {
            return new ReplayRequest(operator, reason, true);
        }
    }

    public record ReplayResult(boolean applied, boolean dryRun, boolean rejected, String detail) {
        public static ReplayResult applied(String detail) {
            return new ReplayResult(true, false, false, detail);
        }

        public static ReplayResult dryRunOk(String detail) {
            return new ReplayResult(false, true, false, detail);
        }

        public static ReplayResult rejected(String detail) {
            return new ReplayResult(false, false, true, detail);
        }
    }
}
