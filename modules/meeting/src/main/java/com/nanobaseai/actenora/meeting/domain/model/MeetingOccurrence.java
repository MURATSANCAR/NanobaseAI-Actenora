package com.nanobaseai.actenora.meeting.domain.model;

import com.nanobaseai.actenora.meeting.domain.event.MeetingDomainEvents;
import com.nanobaseai.actenora.meeting.domain.exception.InvalidDateRangeException;
import com.nanobaseai.actenora.meeting.domain.exception.OptimisticLockConflictException;
import com.nanobaseai.actenora.meeting.domain.exception.TenantIsolationViolationException;
import com.nanobaseai.actenora.meeting.domain.service.MeetingOccurrenceStateMachine;
import com.nanobaseai.actenora.sharedkernel.domain.DomainEvent;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root for a single meeting occurrence (the primary "meeting" resource).
 */
public final class MeetingOccurrence {

    private final UUID id;
    private final TenantId tenantId;
    private final UUID meetingSeriesId;
    private final UUID businessContextId;
    private String graphEventImmutableId;
    private String icalUid;
    private Instant originalStartAt;
    private String teamsMeetingId;
    private String chatId;
    private String joinWebUrl;
    private String title;
    private UUID organizerUserId;
    private Instant scheduledStartAt;
    private Instant scheduledEndAt;
    private Instant actualStartAt;
    private Instant actualEndAt;
    private MeetingOccurrenceStatus status;
    private ProcessingPriority processingPriority;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private MeetingOccurrence(
            UUID id,
            TenantId tenantId,
            UUID meetingSeriesId,
            UUID businessContextId,
            String graphEventImmutableId,
            String icalUid,
            Instant originalStartAt,
            String teamsMeetingId,
            String chatId,
            String joinWebUrl,
            String title,
            UUID organizerUserId,
            Instant scheduledStartAt,
            Instant scheduledEndAt,
            Instant actualStartAt,
            Instant actualEndAt,
            MeetingOccurrenceStatus status,
            ProcessingPriority processingPriority,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.meetingSeriesId = Objects.requireNonNull(meetingSeriesId, "meetingSeriesId");
        this.businessContextId = Objects.requireNonNull(businessContextId, "businessContextId");
        this.graphEventImmutableId = blankToNull(graphEventImmutableId);
        this.icalUid = blankToNull(icalUid);
        this.originalStartAt = originalStartAt;
        this.teamsMeetingId = blankToNull(teamsMeetingId);
        this.chatId = blankToNull(chatId);
        this.joinWebUrl = blankToNull(joinWebUrl);
        this.title = requireText(title, "title");
        this.organizerUserId = Objects.requireNonNull(organizerUserId, "organizerUserId");
        validateDateRange(scheduledStartAt, scheduledEndAt);
        this.scheduledStartAt = Objects.requireNonNull(scheduledStartAt, "scheduledStartAt");
        this.scheduledEndAt = Objects.requireNonNull(scheduledEndAt, "scheduledEndAt");
        this.actualStartAt = actualStartAt;
        this.actualEndAt = actualEndAt;
        this.status = Objects.requireNonNull(status, "status");
        this.processingPriority = Objects.requireNonNull(processingPriority, "processingPriority");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    public static MeetingOccurrence create(
            TenantId tenantId,
            UUID meetingSeriesId,
            UUID businessContextId,
            String graphEventImmutableId,
            String icalUid,
            Instant originalStartAt,
            String teamsMeetingId,
            String chatId,
            String joinWebUrl,
            String title,
            UUID organizerUserId,
            Instant scheduledStartAt,
            Instant scheduledEndAt,
            ProcessingPriority processingPriority,
            Instant now
    ) {
        MeetingOccurrence occurrence = new MeetingOccurrence(
                UUID.randomUUID(),
                tenantId,
                meetingSeriesId,
                businessContextId,
                graphEventImmutableId,
                icalUid,
                originalStartAt != null ? originalStartAt : scheduledStartAt,
                teamsMeetingId,
                chatId,
                joinWebUrl,
                title,
                organizerUserId,
                scheduledStartAt,
                scheduledEndAt,
                null,
                null,
                MeetingOccurrenceStatus.DRAFT,
                processingPriority == null ? ProcessingPriority.NORMAL : processingPriority,
                now,
                now,
                0L
        );
        occurrence.domainEvents.add(MeetingDomainEvents.MeetingCreated.of(
                tenantId, occurrence.id, businessContextId, occurrence.title, now
        ));
        return occurrence;
    }

    public static MeetingOccurrence rehydrate(
            UUID id,
            TenantId tenantId,
            UUID meetingSeriesId,
            UUID businessContextId,
            String graphEventImmutableId,
            String icalUid,
            Instant originalStartAt,
            String teamsMeetingId,
            String chatId,
            String joinWebUrl,
            String title,
            UUID organizerUserId,
            Instant scheduledStartAt,
            Instant scheduledEndAt,
            Instant actualStartAt,
            Instant actualEndAt,
            MeetingOccurrenceStatus status,
            ProcessingPriority processingPriority,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        return new MeetingOccurrence(
                id, tenantId, meetingSeriesId, businessContextId, graphEventImmutableId, icalUid,
                originalStartAt, teamsMeetingId, chatId, joinWebUrl, title, organizerUserId,
                scheduledStartAt, scheduledEndAt, actualStartAt, actualEndAt, status, processingPriority,
                createdAt, updatedAt, version
        );
    }

    public void update(
            String title,
            Instant scheduledStartAt,
            Instant scheduledEndAt,
            String graphEventImmutableId,
            String icalUid,
            Instant originalStartAt,
            String teamsMeetingId,
            String chatId,
            String joinWebUrl,
            ProcessingPriority processingPriority,
            long expectedVersion,
            Instant now
    ) {
        assertVersion(expectedVersion);
        if (title != null) {
            this.title = requireText(title, "title");
        }
        Instant start = scheduledStartAt != null ? scheduledStartAt : this.scheduledStartAt;
        Instant end = scheduledEndAt != null ? scheduledEndAt : this.scheduledEndAt;
        validateDateRange(start, end);
        this.scheduledStartAt = start;
        this.scheduledEndAt = end;
        if (graphEventImmutableId != null) {
            this.graphEventImmutableId = blankToNull(graphEventImmutableId);
        }
        if (icalUid != null) {
            this.icalUid = blankToNull(icalUid);
        }
        if (originalStartAt != null) {
            this.originalStartAt = originalStartAt;
        }
        if (teamsMeetingId != null) {
            this.teamsMeetingId = blankToNull(teamsMeetingId);
        }
        if (chatId != null) {
            this.chatId = blankToNull(chatId);
        }
        if (joinWebUrl != null) {
            this.joinWebUrl = blankToNull(joinWebUrl);
        }
        if (processingPriority != null && processingPriority != this.processingPriority) {
            ProcessingPriority previous = this.processingPriority;
            this.processingPriority = processingPriority;
            domainEvents.add(MeetingDomainEvents.MeetingPriorityChanged.of(
                    tenantId, id, previous, processingPriority, now
            ));
        }
        touch(expectedVersion, now);
    }

    public void transitionTo(MeetingOccurrenceStatus target, long expectedVersion, Instant now) {
        assertVersion(expectedVersion);
        MeetingOccurrenceStatus previous = this.status;
        MeetingOccurrenceStateMachine.assertTransition(previous, target);
        this.status = target;
        switch (target) {
            case SCHEDULED -> domainEvents.add(MeetingDomainEvents.MeetingScheduled.of(
                    tenantId, id, scheduledStartAt, scheduledEndAt, now
            ));
            case IN_PROGRESS -> {
                this.actualStartAt = now;
                domainEvents.add(MeetingDomainEvents.MeetingStarted.of(tenantId, id, actualStartAt, now));
            }
            case ENDED -> {
                this.actualEndAt = now;
                domainEvents.add(MeetingDomainEvents.MeetingEnded.of(tenantId, id, actualEndAt, now));
            }
            case CANCELLED -> domainEvents.add(MeetingDomainEvents.MeetingCancelled.of(
                    tenantId, id, previous, now
            ));
            default -> {
            }
        }
        touch(expectedVersion, now);
    }

    public void changePriority(ProcessingPriority newPriority, long expectedVersion, Instant now) {
        assertVersion(expectedVersion);
        Objects.requireNonNull(newPriority, "newPriority");
        if (newPriority == this.processingPriority) {
            return;
        }
        ProcessingPriority previous = this.processingPriority;
        this.processingPriority = newPriority;
        domainEvents.add(MeetingDomainEvents.MeetingPriorityChanged.of(
                tenantId, id, previous, newPriority, now
        ));
        touch(expectedVersion, now);
    }

    public List<DomainEvent> pullDomainEvents() {
        if (domainEvents.isEmpty()) {
            return List.of();
        }
        List<DomainEvent> copy = List.copyOf(domainEvents);
        domainEvents.clear();
        return copy;
    }

    public List<DomainEvent> peekDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void assertVersion(long expectedVersion) {
        if (this.version != expectedVersion) {
            throw new OptimisticLockConflictException(id, expectedVersion);
        }
    }

    public void assertTenant(TenantId tenantId) {
        if (!this.tenantId.equals(tenantId)) {
            throw new TenantIsolationViolationException();
        }
    }

    private void touch(long expectedVersion, Instant now) {
        this.updatedAt = now;
        this.version = expectedVersion + 1;
    }

    public static void validateDateRange(Instant start, Instant end) {
        if (start == null || end == null) {
            throw new InvalidDateRangeException("scheduledStartAt and scheduledEndAt are required");
        }
        if (!end.isAfter(start)) {
            throw new InvalidDateRangeException("scheduledEndAt must be after scheduledStartAt");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public UUID meetingSeriesId() { return meetingSeriesId; }
    public UUID businessContextId() { return businessContextId; }
    public String graphEventImmutableId() { return graphEventImmutableId; }
    public String icalUid() { return icalUid; }
    public Instant originalStartAt() { return originalStartAt; }
    public String teamsMeetingId() { return teamsMeetingId; }
    public String chatId() { return chatId; }
    public String joinWebUrl() { return joinWebUrl; }
    public String title() { return title; }
    public UUID organizerUserId() { return organizerUserId; }
    public Instant scheduledStartAt() { return scheduledStartAt; }
    public Instant scheduledEndAt() { return scheduledEndAt; }
    public Instant actualStartAt() { return actualStartAt; }
    public Instant actualEndAt() { return actualEndAt; }
    public MeetingOccurrenceStatus status() { return status; }
    public ProcessingPriority processingPriority() { return processingPriority; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
