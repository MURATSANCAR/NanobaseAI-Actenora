package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.OffsetDateTime;
import java.time.ZoneId;
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

    static MeetingOccurrenceClockPort unsupported() {
        return (tenantId, meetingOccurrenceId) -> Optional.empty();
    }
}
