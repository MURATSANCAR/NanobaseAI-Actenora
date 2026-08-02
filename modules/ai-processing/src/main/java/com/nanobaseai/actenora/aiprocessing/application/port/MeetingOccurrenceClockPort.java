package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves meeting occurrence start time for relative-date resolution.
 */
public interface MeetingOccurrenceClockPort {

    Optional<OffsetDateTime> scheduledStart(TenantId tenantId, UUID meetingOccurrenceId);

    default ZoneId timezone(TenantId tenantId, UUID meetingOccurrenceId) {
        return ZoneId.of("Europe/Istanbul");
    }

    /**
     * Calendar invitee display names (and email local-parts) for owner binding / validation.
     */
    default List<String> participantDisplayNames(TenantId tenantId, UUID meetingOccurrenceId) {
        return List.of();
    }

    static MeetingOccurrenceClockPort unsupported() {
        return (tenantId, meetingOccurrenceId) -> Optional.empty();
    }
}
