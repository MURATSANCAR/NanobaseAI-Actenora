package com.nanobaseai.actenora.security.meeting;

import com.nanobaseai.actenora.aiprocessing.application.port.MeetingOccurrenceClockPort;
import com.nanobaseai.actenora.meeting.application.port.MeetingOccurrenceRepository;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MeetingOccurrenceClockAdapter implements MeetingOccurrenceClockPort {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Istanbul");

    private final MeetingOccurrenceRepository repository;

    public MeetingOccurrenceClockAdapter(MeetingOccurrenceRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public Optional<OffsetDateTime> scheduledStart(TenantId tenantId, UUID meetingOccurrenceId) {
        return repository.findByIdAndTenantId(meetingOccurrenceId, tenantId)
                .map(o -> o.scheduledStartAt())
                .filter(Objects::nonNull)
                .map(instant -> instant.atZone(DEFAULT_ZONE).toOffsetDateTime());
    }

    @Override
    public ZoneId timezone(TenantId tenantId, UUID meetingOccurrenceId) {
        return DEFAULT_ZONE;
    }
}
