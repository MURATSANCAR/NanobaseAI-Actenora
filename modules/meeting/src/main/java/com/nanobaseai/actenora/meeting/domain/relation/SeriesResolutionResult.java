package com.nanobaseai.actenora.meeting.domain.relation;

import com.nanobaseai.actenora.meeting.domain.continuity.ImmutableEventIdentity;
import com.nanobaseai.actenora.meeting.domain.continuity.OccurrenceContinuityKey;
import com.nanobaseai.actenora.meeting.domain.continuity.OccurrenceIdentitySnapshot;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Outcome of resolving an inbound calendar event against known occurrences.
 */
public record SeriesResolutionResult(
        ResolutionStatus status,
        Optional<UUID> matchedOccurrenceId,
        Optional<OccurrenceContinuityKey> continuityKey,
        Optional<ImmutableEventIdentity> immutableEventIdentity,
        List<UUID> sameSeriesOccurrenceIds,
        String detail
) {

    public SeriesResolutionResult {
        Objects.requireNonNull(status, "status");
        matchedOccurrenceId = matchedOccurrenceId == null ? Optional.empty() : matchedOccurrenceId;
        continuityKey = continuityKey == null ? Optional.empty() : continuityKey;
        immutableEventIdentity = immutableEventIdentity == null ? Optional.empty() : immutableEventIdentity;
        sameSeriesOccurrenceIds = List.copyOf(Objects.requireNonNull(sameSeriesOccurrenceIds, "sameSeriesOccurrenceIds"));
        Objects.requireNonNull(detail, "detail");
    }

    public enum ResolutionStatus {
        MATCHED_OCCURRENCE,
        MATCHED_SERIES_NEW_OCCURRENCE,
        NO_MATCH,
        AMBIGUOUS
    }

    public static SeriesResolutionResult matched(OccurrenceIdentitySnapshot snapshot, List<UUID> sameSeriesIds) {
        return new SeriesResolutionResult(
                ResolutionStatus.MATCHED_OCCURRENCE,
                Optional.of(snapshot.occurrenceId()),
                snapshot.continuityKeyOptional(),
                Optional.of(snapshot.immutableEventIdentity()),
                sameSeriesIds,
                "Matched by continuity key and/or immutable event identity"
        );
    }

    public static SeriesResolutionResult matchedSeriesNewOccurrence(
            OccurrenceContinuityKey key,
            ImmutableEventIdentity identity,
            List<UUID> sameSeriesIds
    ) {
        return new SeriesResolutionResult(
                ResolutionStatus.MATCHED_SERIES_NEW_OCCURRENCE,
                Optional.empty(),
                Optional.of(key),
                Optional.of(identity),
                sameSeriesIds,
                "Series master and iCal UID matched; originalStartAt is a new occurrence"
        );
    }

    public static SeriesResolutionResult noMatch(String detail) {
        return new SeriesResolutionResult(
                ResolutionStatus.NO_MATCH,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                detail
        );
    }

    public static SeriesResolutionResult ambiguous(List<UUID> candidates, String detail) {
        return new SeriesResolutionResult(
                ResolutionStatus.AMBIGUOUS,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                candidates,
                detail
        );
    }
}
