package com.nanobaseai.actenora.meeting.domain.continuity;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Read model of occurrence identity fields used for series resolution and relations.
 * Join URL is retained for display only and must never drive identity matching.
 */
public record OccurrenceIdentitySnapshot(
        UUID occurrenceId,
        UUID tenantId,
        UUID meetingSeriesId,
        UUID businessContextId,
        ImmutableEventIdentity immutableEventIdentity,
        OccurrenceContinuityKey continuityKey,
        String joinWebUrl,
        String title,
        Instant scheduledStartAt,
        Instant scheduledEndAt
) {

    public OccurrenceIdentitySnapshot {
        Objects.requireNonNull(occurrenceId, "occurrenceId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(immutableEventIdentity, "immutableEventIdentity");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(scheduledStartAt, "scheduledStartAt");
    }

    public Optional<OccurrenceContinuityKey> continuityKeyOptional() {
        return Optional.ofNullable(continuityKey);
    }

    public boolean belongsToTenant(UUID expectedTenantId) {
        return tenantId.equals(expectedTenantId);
    }

    /**
     * Title changes (renames) do not alter identity.
     */
    public OccurrenceIdentitySnapshot withTitle(String newTitle) {
        return new OccurrenceIdentitySnapshot(
                occurrenceId,
                tenantId,
                meetingSeriesId,
                businessContextId,
                immutableEventIdentity,
                continuityKey,
                joinWebUrl,
                newTitle,
                scheduledStartAt,
                scheduledEndAt
        );
    }

    /**
     * Moved occurrence keeps originalStartAt in the continuity key; scheduled start may change.
     */
    public OccurrenceIdentitySnapshot withMovedSchedule(Instant newScheduledStart, Instant newScheduledEnd) {
        return new OccurrenceIdentitySnapshot(
                occurrenceId,
                tenantId,
                meetingSeriesId,
                businessContextId,
                immutableEventIdentity,
                continuityKey,
                joinWebUrl,
                title,
                newScheduledStart,
                newScheduledEnd
        );
    }
}
