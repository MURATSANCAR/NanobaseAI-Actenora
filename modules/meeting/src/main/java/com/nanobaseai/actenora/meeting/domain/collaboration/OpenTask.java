package com.nanobaseai.actenora.meeting.domain.collaboration;

import com.nanobaseai.actenora.meeting.domain.exception.TenantIsolationViolationException;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class OpenTask {

    private final UUID id;
    private final TenantId tenantId;
    private final UUID meetingOccurrenceId;
    private final String title;
    private final UUID assigneeUserId;
    private final boolean open;
    private final UUID createdByUserId;
    private final Instant createdAt;
    private final UUID sourceMeetingOccurrenceId;

    private OpenTask(
            UUID id,
            TenantId tenantId,
            UUID meetingOccurrenceId,
            String title,
            UUID assigneeUserId,
            boolean open,
            UUID createdByUserId,
            Instant createdAt,
            UUID sourceMeetingOccurrenceId
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.meetingOccurrenceId = Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        this.title = requireText(title, "title");
        this.assigneeUserId = assigneeUserId;
        this.open = open;
        this.createdByUserId = Objects.requireNonNull(createdByUserId, "createdByUserId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.sourceMeetingOccurrenceId = sourceMeetingOccurrenceId;
    }

    public static OpenTask create(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            String title,
            UUID assigneeUserId,
            UUID createdByUserId,
            Instant now,
            UUID sourceMeetingOccurrenceId
    ) {
        return new OpenTask(
                UUID.randomUUID(),
                tenantId,
                meetingOccurrenceId,
                title,
                assigneeUserId,
                true,
                createdByUserId,
                now,
                sourceMeetingOccurrenceId
        );
    }

    public static OpenTask rehydrate(
            UUID id,
            TenantId tenantId,
            UUID meetingOccurrenceId,
            String title,
            UUID assigneeUserId,
            boolean open,
            UUID createdByUserId,
            Instant createdAt,
            UUID sourceMeetingOccurrenceId
    ) {
        return new OpenTask(
                id, tenantId, meetingOccurrenceId, title, assigneeUserId, open, createdByUserId, createdAt, sourceMeetingOccurrenceId
        );
    }

    public OpenTask close() {
        return new OpenTask(
                id, tenantId, meetingOccurrenceId, title, assigneeUserId, false, createdByUserId, createdAt, sourceMeetingOccurrenceId
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

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public UUID meetingOccurrenceId() { return meetingOccurrenceId; }
    public String title() { return title; }
    public UUID assigneeUserId() { return assigneeUserId; }
    public boolean open() { return open; }
    public UUID createdByUserId() { return createdByUserId; }
    public Instant createdAt() { return createdAt; }
    public UUID sourceMeetingOccurrenceId() { return sourceMeetingOccurrenceId; }
}
