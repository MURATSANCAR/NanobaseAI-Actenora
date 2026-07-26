package com.nanobaseai.actenora.audit.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only audit entry (FAZ 18 timeline).
 */
public final class AuditEntry {

    private final UUID id;
    private final UUID tenantId;
    private final String actorId;
    private final String action;
    private final String resourceType;
    private final UUID resourceId;
    private final Map<String, Object> metadata;
    private final Instant occurredAt;

    private AuditEntry(
            UUID id,
            UUID tenantId,
            String actorId,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> metadata,
            Instant occurredAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.action = requireText(action, "action");
        this.resourceType = requireText(resourceType, "resourceType");
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
        this.metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static AuditEntry append(
            UUID tenantId,
            String actorId,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> metadata,
            Instant occurredAt
    ) {
        return new AuditEntry(
                UUID.randomUUID(),
                tenantId,
                actorId,
                action,
                resourceType,
                resourceId,
                metadata == null ? Map.of() : metadata,
                occurredAt
        );
    }

    /** Rehydrate from durable store (read path only). */
    public static AuditEntry rehydrate(
            UUID id,
            UUID tenantId,
            String actorId,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> metadata,
            Instant occurredAt
    ) {
        return new AuditEntry(
                id,
                tenantId,
                actorId,
                action,
                resourceType,
                resourceId,
                metadata == null ? Map.of() : metadata,
                occurredAt
        );
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public String actorId() { return actorId; }
    public String action() { return action; }
    public String resourceType() { return resourceType; }
    public UUID resourceId() { return resourceId; }
    public Map<String, Object> metadata() { return metadata; }
    public Instant occurredAt() { return occurredAt; }
}
