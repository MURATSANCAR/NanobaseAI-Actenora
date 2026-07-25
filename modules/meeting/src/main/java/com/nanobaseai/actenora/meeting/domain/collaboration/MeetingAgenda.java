package com.nanobaseai.actenora.meeting.domain.collaboration;

import com.nanobaseai.actenora.meeting.domain.exception.OptimisticLockConflictException;
import com.nanobaseai.actenora.meeting.domain.exception.TenantIsolationViolationException;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class MeetingAgenda {

    private final UUID id;
    private final TenantId tenantId;
    private final UUID meetingOccurrenceId;
    private List<String> items;
    private final UUID createdByUserId;
    private UUID updatedByUserId;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    private MeetingAgenda(
            UUID id,
            TenantId tenantId,
            UUID meetingOccurrenceId,
            List<String> items,
            UUID createdByUserId,
            UUID updatedByUserId,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.meetingOccurrenceId = Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        this.items = List.copyOf(Objects.requireNonNullElse(items, List.of()));
        this.createdByUserId = Objects.requireNonNull(createdByUserId, "createdByUserId");
        this.updatedByUserId = Objects.requireNonNull(updatedByUserId, "updatedByUserId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    public static MeetingAgenda create(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            List<String> items,
            UUID actorUserId,
            Instant now
    ) {
        return new MeetingAgenda(
                UUID.randomUUID(),
                tenantId,
                meetingOccurrenceId,
                sanitizeItems(items),
                actorUserId,
                actorUserId,
                now,
                now,
                0L
        );
    }

    public static MeetingAgenda rehydrate(
            UUID id,
            TenantId tenantId,
            UUID meetingOccurrenceId,
            List<String> items,
            UUID createdByUserId,
            UUID updatedByUserId,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        return new MeetingAgenda(
                id, tenantId, meetingOccurrenceId, items, createdByUserId, updatedByUserId, createdAt, updatedAt, version
        );
    }

    public void replaceItems(List<String> items, UUID actorUserId, Instant now, long expectedVersion) {
        if (this.version != expectedVersion) {
            throw new OptimisticLockConflictException(id, expectedVersion);
        }
        this.items = List.copyOf(sanitizeItems(items));
        this.updatedByUserId = Objects.requireNonNull(actorUserId, "actorUserId");
        this.updatedAt = Objects.requireNonNull(now, "now");
        this.version++;
    }

    public void assertTenant(TenantId tenantId) {
        if (!this.tenantId.equals(tenantId)) {
            throw new TenantIsolationViolationException();
        }
    }

    private static List<String> sanitizeItems(List<String> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>();
        for (String item : items) {
            if (item != null && !item.isBlank()) {
                cleaned.add(item.trim());
            }
        }
        return cleaned;
    }

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public UUID meetingOccurrenceId() { return meetingOccurrenceId; }
    public List<String> items() { return items; }
    public UUID createdByUserId() { return createdByUserId; }
    public UUID updatedByUserId() { return updatedByUserId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
