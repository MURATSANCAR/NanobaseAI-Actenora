package com.nanobaseai.actenora.security.meeting;

import com.nanobaseai.actenora.aiprocessing.application.port.MeetingOccurrenceClockPort;
import com.nanobaseai.actenora.meeting.application.port.MeetingOccurrenceRepository;
import com.nanobaseai.actenora.meeting.application.port.MeetingParticipantRepository;
import com.nanobaseai.actenora.meeting.domain.model.AttendanceStatus;
import com.nanobaseai.actenora.meeting.domain.model.MeetingParticipant;
import com.nanobaseai.actenora.sharedkernel.domain.PersonIdentityNormalizer;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MeetingOccurrenceClockAdapter implements MeetingOccurrenceClockPort {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Istanbul");

    private final MeetingOccurrenceRepository repository;
    private final MeetingParticipantRepository participantRepository;

    public MeetingOccurrenceClockAdapter(MeetingOccurrenceRepository repository) {
        this(repository, null);
    }

    public MeetingOccurrenceClockAdapter(
            MeetingOccurrenceRepository repository,
            MeetingParticipantRepository participantRepository
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.participantRepository = participantRepository;
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

    @Override
    public List<String> participantDisplayNames(TenantId tenantId, UUID meetingOccurrenceId) {
        if (participantRepository == null) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (MeetingParticipant p : participantRepository.findByMeetingOccurrenceIdAndTenantId(
                meetingOccurrenceId, tenantId)) {
            if (p.attendanceStatus() != AttendanceStatus.JOINED
                    && p.attendanceStatus() != AttendanceStatus.LEFT) {
                continue;
            }
            if (p.displayName() != null && !p.displayName().isBlank()) {
                names.add(PersonIdentityNormalizer.displayName(p.displayName()));
            }
            if (p.email() != null && !p.email().isBlank()) {
                names.add(PersonIdentityNormalizer.displayName(p.email()));
            }
        }
        return List.copyOf(PersonIdentityNormalizer.canonicalRoster(names).values());
    }
}
