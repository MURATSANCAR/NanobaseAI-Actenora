package com.nanobaseai.actenora.meeting.domain.collaboration;

import com.nanobaseai.actenora.meeting.domain.exception.TenantIsolationViolationException;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class MeetingMarker {

    private final UUID id;
    private final TenantId tenantId;
    private final UUID meetingOccurrenceId;
    private final MarkerType type;
    private final String body;
    private final long offsetMs;
    private final UUID createdByUserId;
    private final Instant createdAt;
    private final String idempotencyKey;

    private MeetingMarker(
            UUID id,
            TenantId tenantId,
            UUID meetingOccurrenceId,
            MarkerType type,
            String body,
            long offsetMs,
            UUID createdByUserId,
            Instant createdAt,
            String idempotencyKey
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.meetingOccurrenceId = Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        this.type = Objects.requireNonNull(type, "type");
        this.body = requireText(body, "body");
        this.offsetMs = offsetMs;
        this.createdByUserId = Objects.requireNonNull(createdByUserId, "createdByUserId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.idempotencyKey = blankToNull(idempotencyKey);
    }

    public static MeetingMarker create(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            MarkerType type,
            String body,
            long offsetMs,
            UUID createdByUserId,
            Instant createdAt,
            String idempotencyKey
    ) {
        return new MeetingMarker(
                UUID.randomUUID(),
                tenantId,
                meetingOccurrenceId,
                type,
                body,
                offsetMs,
                createdByUserId,
                createdAt,
                idempotencyKey
        );
    }

    public static MeetingMarker rehydrate(
            UUID id,
            TenantId tenantId,
            UUID meetingOccurrenceId,
            MarkerType type,
            String body,
            long offsetMs,
            UUID createdByUserId,
            Instant createdAt,
            String idempotencyKey
    ) {
        return new MeetingMarker(
                id, tenantId, meetingOccurrenceId, type, body, offsetMs, createdByUserId, createdAt, idempotencyKey
        );
    }

    public void assertTenant(TenantId tenantId) {
        if (!this.tenantId.equals(tenantId)) {
            throw new TenantIsolationViolationException();
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public UUID meetingOccurrenceId() { return meetingOccurrenceId; }
    public MarkerType type() { return type; }
    public String body() { return body; }
    public long offsetMs() { return offsetMs; }
    public UUID createdByUserId() { return createdByUserId; }
    public Instant createdAt() { return createdAt; }
    public String idempotencyKey() { return idempotencyKey; }
}
