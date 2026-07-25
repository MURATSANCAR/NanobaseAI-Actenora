package com.nanobaseai.actenora.meeting.domain.relation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Previous/next projection used when preparing the next meeting brief.
 */
public record ContinuityProjection(
        UUID occurrenceId,
        UUID tenantId,
        Optional<UUID> previousOccurrenceId,
        Optional<UUID> nextOccurrenceId,
        Optional<UUID> seriesId,
        Optional<UUID> businessContextId
) {

    public ContinuityProjection {
        Objects.requireNonNull(occurrenceId, "occurrenceId");
        Objects.requireNonNull(tenantId, "tenantId");
        previousOccurrenceId = previousOccurrenceId == null ? Optional.empty() : previousOccurrenceId;
        nextOccurrenceId = nextOccurrenceId == null ? Optional.empty() : nextOccurrenceId;
        seriesId = seriesId == null ? Optional.empty() : seriesId;
        businessContextId = businessContextId == null ? Optional.empty() : businessContextId;
    }
}
