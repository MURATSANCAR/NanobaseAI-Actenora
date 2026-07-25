package com.nanobaseai.actenora.meeting.domain.collaboration;

import com.nanobaseai.actenora.meeting.domain.exception.OptimisticLockConflictException;
import com.nanobaseai.actenora.meeting.domain.exception.TenantIsolationViolationException;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class SharedNote {

    private final UUID id;
    private final TenantId tenantId;
    private final UUID meetingOccurrenceId;
    private String body;
    private final UUID createdByUserId;
    private UUID updatedByUserId;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    private SharedNote(
            UUID id,
            TenantId tenantId,
            UUID meetingOccurrenceId,
            String body,
            UUID createdByUserId,
            UUID updatedByUserId,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.meetingOccurrenceId = Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        this.body = body == null ? "" : body;
        this.createdByUserId = Objects.requireNonNull(createdByUserId, "createdByUserId");
        this.updatedByUserId = Objects.requireNonNull(updatedByUserId, "updatedByUserId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    public static SharedNote create(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            String body,
            UUID actorUserId,
            Instant now
    ) {
        return new SharedNote(
                UUID.randomUUID(),
                tenantId,
                meetingOccurrenceId,
                body,
                actorUserId,
                actorUserId,
                now,
                now,
                0L
        );
    }

    public static SharedNote rehydrate(
            UUID id,
            TenantId tenantId,
            UUID meetingOccurrenceId,
            String body,
            UUID createdByUserId,
            UUID updatedByUserId,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        return new SharedNote(
                id, tenantId, meetingOccurrenceId, body, createdByUserId, updatedByUserId, createdAt, updatedAt, version
        );
    }

    public void updateBody(String body, UUID actorUserId, Instant now, long expectedVersion) {
        if (this.version != expectedVersion) {
            throw new OptimisticLockConflictException(id, expectedVersion);
        }
        this.body = body == null ? "" : body;
        this.updatedByUserId = Objects.requireNonNull(actorUserId, "actorUserId");
        this.updatedAt = Objects.requireNonNull(now, "now");
        this.version++;
    }

    public void assertTenant(TenantId tenantId) {
        if (!this.tenantId.equals(tenantId)) {
            throw new TenantIsolationViolationException();
        }
    }

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public UUID meetingOccurrenceId() { return meetingOccurrenceId; }
    public String body() { return body; }
    public UUID createdByUserId() { return createdByUserId; }
    public UUID updatedByUserId() { return updatedByUserId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
