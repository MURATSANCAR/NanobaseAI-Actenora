package com.nanobaseai.actenora.meeting.domain.collaboration;

import com.nanobaseai.actenora.meeting.domain.exception.OptimisticLockConflictException;
import com.nanobaseai.actenora.meeting.domain.exception.TenantIsolationViolationException;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class PrivateNote {

    private final UUID id;
    private final TenantId tenantId;
    private final UUID meetingOccurrenceId;
    private final UUID ownerUserId;
    private String body;
    private boolean aiUseAllowed;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    private PrivateNote(
            UUID id,
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID ownerUserId,
            String body,
            boolean aiUseAllowed,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.meetingOccurrenceId = Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        this.ownerUserId = Objects.requireNonNull(ownerUserId, "ownerUserId");
        this.body = body == null ? "" : body;
        this.aiUseAllowed = aiUseAllowed;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    public static PrivateNote create(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID ownerUserId,
            String body,
            Instant now
    ) {
        return new PrivateNote(
                UUID.randomUUID(),
                tenantId,
                meetingOccurrenceId,
                ownerUserId,
                body,
                false,
                now,
                now,
                0L
        );
    }

    public static PrivateNote rehydrate(
            UUID id,
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID ownerUserId,
            String body,
            boolean aiUseAllowed,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        return new PrivateNote(
                id, tenantId, meetingOccurrenceId, ownerUserId, body, aiUseAllowed, createdAt, updatedAt, version
        );
    }

    public void updateBody(String body, UUID actorUserId, Instant now, long expectedVersion) {
        assertOwner(actorUserId);
        if (this.version != expectedVersion) {
            throw new OptimisticLockConflictException(id, expectedVersion);
        }
        this.body = body == null ? "" : body;
        this.updatedAt = Objects.requireNonNull(now, "now");
        this.version++;
    }

    public void grantAiUse(UUID actorUserId, Instant now) {
        assertOwner(actorUserId);
        this.aiUseAllowed = true;
        this.updatedAt = Objects.requireNonNull(now, "now");
        this.version++;
    }

    public void revokeAiUse(UUID actorUserId, Instant now) {
        assertOwner(actorUserId);
        this.aiUseAllowed = false;
        this.updatedAt = Objects.requireNonNull(now, "now");
        this.version++;
    }

    public void assertOwner(UUID actorUserId) {
        if (!ownerUserId.equals(Objects.requireNonNull(actorUserId, "actorUserId"))) {
            throw new PrivateNoteAccessDeniedException(id);
        }
    }

    public void assertTenant(TenantId tenantId) {
        if (!this.tenantId.equals(tenantId)) {
            throw new TenantIsolationViolationException();
        }
    }

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public UUID meetingOccurrenceId() { return meetingOccurrenceId; }
    public UUID ownerUserId() { return ownerUserId; }
    public String body() { return body; }
    public boolean aiUseAllowed() { return aiUseAllowed; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
