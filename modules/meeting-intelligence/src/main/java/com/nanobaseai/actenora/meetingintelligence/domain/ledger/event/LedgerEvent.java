package com.nanobaseai.actenora.meetingintelligence.domain.ledger.event;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only ledger fact. Projections rebuild exclusively from these events —
 * never via operational cross-schema joins.
 */
public final class LedgerEvent {

    private final UUID eventId;
    private final TenantId tenantId;
    private final LedgerEventType type;
    private final String aggregateType;
    private final UUID aggregateId;
    private final UUID meetingOccurrenceId;
    private final Instant occurredAt;
    private final long sequence;
    private final Map<String, String> payload;

    private LedgerEvent(
            UUID eventId,
            TenantId tenantId,
            LedgerEventType type,
            String aggregateType,
            UUID aggregateId,
            UUID meetingOccurrenceId,
            Instant occurredAt,
            long sequence,
            Map<String, String> payload
    ) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.type = Objects.requireNonNull(type, "type");
        this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId");
        this.meetingOccurrenceId = meetingOccurrenceId;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.sequence = sequence;
        this.payload = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(payload, "payload")));
    }

    public static LedgerEvent create(
            TenantId tenantId,
            LedgerEventType type,
            String aggregateType,
            UUID aggregateId,
            UUID meetingOccurrenceId,
            Instant occurredAt,
            long sequence,
            Map<String, String> payload
    ) {
        return new LedgerEvent(
                UUID.randomUUID(),
                tenantId,
                type,
                aggregateType,
                aggregateId,
                meetingOccurrenceId,
                occurredAt,
                sequence,
                payload == null ? Map.of() : payload
        );
    }

    public static LedgerEvent rehydrate(
            UUID eventId,
            TenantId tenantId,
            LedgerEventType type,
            String aggregateType,
            UUID aggregateId,
            UUID meetingOccurrenceId,
            Instant occurredAt,
            long sequence,
            Map<String, String> payload
    ) {
        return new LedgerEvent(
                eventId, tenantId, type, aggregateType, aggregateId,
                meetingOccurrenceId, occurredAt, sequence, payload == null ? Map.of() : payload
        );
    }

    public String require(String key) {
        String value = payload.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing payload key: " + key + " on " + type);
        }
        return value;
    }

    public String optional(String key) {
        return payload.get(key);
    }

    public UUID eventId() { return eventId; }
    public TenantId tenantId() { return tenantId; }
    public LedgerEventType type() { return type; }
    public String aggregateType() { return aggregateType; }
    public UUID aggregateId() { return aggregateId; }
    public UUID meetingOccurrenceId() { return meetingOccurrenceId; }
    public Instant occurredAt() { return occurredAt; }
    public long sequence() { return sequence; }
    public Map<String, String> payload() { return payload; }
}
