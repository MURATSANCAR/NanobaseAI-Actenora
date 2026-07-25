package com.nanobaseai.actenora.sharedkernel.messaging;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Propagated correlation / causation / trace identifiers across publish and consume.
 */
public final class CorrelationContext {

    private static final ThreadLocal<CorrelationContext> CURRENT = new ThreadLocal<>();

    private final UUID correlationId;
    private final UUID causationId;
    private final String traceId;

    public CorrelationContext(UUID correlationId, UUID causationId, String traceId) {
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId");
        this.causationId = causationId;
        this.traceId = traceId;
    }

    public static CorrelationContext newRoot(String traceId) {
        UUID id = UUID.randomUUID();
        return new CorrelationContext(id, null, traceId);
    }

    public static CorrelationContext continueFrom(UUID correlationId, UUID priorEventId, String traceId) {
        return new CorrelationContext(
                Objects.requireNonNull(correlationId, "correlationId"),
                priorEventId,
                traceId);
    }

    public UUID correlationId() {
        return correlationId;
    }

    public Optional<UUID> causationId() {
        return Optional.ofNullable(causationId);
    }

    public Optional<String> traceId() {
        return Optional.ofNullable(traceId);
    }

    public CorrelationContext cause(UUID eventId) {
        return new CorrelationContext(correlationId, eventId, traceId);
    }

    public static void set(CorrelationContext context) {
        CURRENT.set(Objects.requireNonNull(context, "context"));
    }

    public static Optional<CorrelationContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Scope open(CorrelationContext context) {
        CorrelationContext previous = CURRENT.get();
        CURRENT.set(context);
        return new Scope(previous);
    }

    public static final class Scope implements AutoCloseable {
        private final CorrelationContext previous;

        private Scope(CorrelationContext previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
