package com.nanobaseai.actenora.meeting.domain.continuity;

import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrence;
import com.nanobaseai.actenora.meeting.domain.model.MeetingSeries;

import java.util.Objects;

/**
 * Maps FAZ 6 occurrence/series aggregates into continuity snapshots used by FAZ 7 resolvers.
 */
public final class OccurrenceIdentityMapper {

    private OccurrenceIdentityMapper() {
    }

    public static OccurrenceIdentitySnapshot from(MeetingOccurrence occurrence, MeetingSeries series) {
        Objects.requireNonNull(occurrence, "occurrence");
        Objects.requireNonNull(series, "series");
        if (!occurrence.meetingSeriesId().equals(series.id())) {
            throw new IllegalArgumentException("occurrence does not belong to the provided series");
        }

        OccurrenceContinuityKey continuityKey = null;
        if (series.graphSeriesMasterId() != null
                && occurrence.icalUid() != null
                && occurrence.originalStartAt() != null) {
            continuityKey = new OccurrenceContinuityKey(
                    series.graphSeriesMasterId(),
                    occurrence.icalUid(),
                    occurrence.originalStartAt()
            );
        }

        ImmutableEventIdentity immutableIdentity = occurrence.graphEventImmutableId() == null
                ? null
                : ImmutableEventIdentity.of(occurrence.graphEventImmutableId());
        if (immutableIdentity == null) {
            throw new IllegalArgumentException("occurrence lacks immutable Graph event identity");
        }

        return new OccurrenceIdentitySnapshot(
                occurrence.id(),
                occurrence.tenantId().value(),
                occurrence.meetingSeriesId(),
                occurrence.businessContextId(),
                immutableIdentity,
                continuityKey,
                occurrence.joinWebUrl(),
                occurrence.title(),
                occurrence.scheduledStartAt(),
                occurrence.scheduledEndAt()
        );
    }
}
