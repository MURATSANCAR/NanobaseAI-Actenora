package com.nanobaseai.actenora.meeting.domain.model;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class MeetingSeries {

    private final UUID id;
    private final TenantId tenantId;
    private final UUID businessContextId;
    private String graphSeriesMasterId;
    private UUID organizerUserId;
    private String title;
    private MeetingType meetingType;
    private MeetingSeriesStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    private MeetingSeries(
            UUID id,
            TenantId tenantId,
            UUID businessContextId,
            String graphSeriesMasterId,
            UUID organizerUserId,
            String title,
            MeetingType meetingType,
            MeetingSeriesStatus status,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.businessContextId = Objects.requireNonNull(businessContextId, "businessContextId");
        this.graphSeriesMasterId = graphSeriesMasterId;
        this.organizerUserId = Objects.requireNonNull(organizerUserId, "organizerUserId");
        this.title = requireText(title, "title");
        this.meetingType = Objects.requireNonNull(meetingType, "meetingType");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    public static MeetingSeries create(
            TenantId tenantId,
            UUID businessContextId,
            String graphSeriesMasterId,
            UUID organizerUserId,
            String title,
            MeetingType meetingType,
            Instant now
    ) {
        return new MeetingSeries(
                UUID.randomUUID(),
                tenantId,
                businessContextId,
                graphSeriesMasterId,
                organizerUserId,
                title,
                meetingType == null ? MeetingType.STANDALONE : meetingType,
                MeetingSeriesStatus.ACTIVE,
                now,
                now,
                0L
        );
    }

    public static MeetingSeries rehydrate(
            UUID id,
            TenantId tenantId,
            UUID businessContextId,
            String graphSeriesMasterId,
            UUID organizerUserId,
            String title,
            MeetingType meetingType,
            MeetingSeriesStatus status,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        return new MeetingSeries(
                id, tenantId, businessContextId, graphSeriesMasterId, organizerUserId,
                title, meetingType, status, createdAt, updatedAt, version
        );
    }

    public void update(String title, MeetingType meetingType, MeetingSeriesStatus status,
                       String graphSeriesMasterId, long expectedVersion, Instant now) {
        assertVersion(expectedVersion);
        if (title != null) {
            this.title = requireText(title, "title");
        }
        if (meetingType != null) {
            this.meetingType = meetingType;
        }
        if (status != null) {
            this.status = status;
        }
        if (graphSeriesMasterId != null) {
            this.graphSeriesMasterId = graphSeriesMasterId;
        }
        this.updatedAt = now;
        this.version = expectedVersion + 1;
    }

    public void assertVersion(long expectedVersion) {
        if (this.version != expectedVersion) {
            throw new com.nanobaseai.actenora.meeting.domain.exception.OptimisticLockConflictException(id, expectedVersion);
        }
    }

    public void assertTenant(TenantId tenantId) {
        if (!this.tenantId.equals(tenantId)) {
            throw new com.nanobaseai.actenora.meeting.domain.exception.TenantIsolationViolationException();
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
    public UUID businessContextId() { return businessContextId; }
    public String graphSeriesMasterId() { return graphSeriesMasterId; }
    public UUID organizerUserId() { return organizerUserId; }
    public String title() { return title; }
    public MeetingType meetingType() { return meetingType; }
    public MeetingSeriesStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
