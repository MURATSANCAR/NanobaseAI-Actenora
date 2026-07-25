package com.nanobaseai.actenora.meeting.api.relation;

import com.nanobaseai.actenora.meeting.domain.relation.ContinuityProjection;

import java.util.Optional;
import java.util.UUID;

public record ContinuityProjectionResponse(
        UUID occurrenceId,
        UUID tenantId,
        Optional<UUID> previousOccurrenceId,
        Optional<UUID> nextOccurrenceId,
        Optional<UUID> seriesId,
        Optional<UUID> businessContextId
) {

    public static ContinuityProjectionResponse from(ContinuityProjection projection) {
        return new ContinuityProjectionResponse(
                projection.occurrenceId(),
                projection.tenantId(),
                projection.previousOccurrenceId(),
                projection.nextOccurrenceId(),
                projection.seriesId(),
                projection.businessContextId()
        );
    }
}
