package com.nanobaseai.actenora.meeting.infrastructure.relation;

import com.nanobaseai.actenora.meeting.application.port.MeetingOccurrenceRepository;
import com.nanobaseai.actenora.meeting.application.port.MeetingSeriesRepository;
import com.nanobaseai.actenora.meeting.application.relation.port.OccurrenceContinuityPort;
import com.nanobaseai.actenora.meeting.domain.continuity.OccurrenceIdentityMapper;
import com.nanobaseai.actenora.meeting.domain.continuity.OccurrenceIdentitySnapshot;
import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrence;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * FAZ 7 continuity reads backed by FAZ 6 occurrence/series repositories.
 */
public final class RepositoryOccurrenceContinuityPort implements OccurrenceContinuityPort {

    private final MeetingOccurrenceRepository occurrenceRepository;
    private final MeetingSeriesRepository seriesRepository;

    public RepositoryOccurrenceContinuityPort(
            MeetingOccurrenceRepository occurrenceRepository,
            MeetingSeriesRepository seriesRepository
    ) {
        this.occurrenceRepository = Objects.requireNonNull(occurrenceRepository, "occurrenceRepository");
        this.seriesRepository = Objects.requireNonNull(seriesRepository, "seriesRepository");
    }

    @Override
    public Optional<OccurrenceIdentitySnapshot> findById(UUID tenantId, UUID occurrenceId) {
        TenantId tid = TenantId.of(tenantId);
        return occurrenceRepository.findByIdAndTenantId(occurrenceId, tid)
                .flatMap(occurrence -> toSnapshot(tid, occurrence));
    }

    @Override
    public List<OccurrenceIdentitySnapshot> findAllByTenant(UUID tenantId) {
        TenantId tid = TenantId.of(tenantId);
        List<OccurrenceIdentitySnapshot> snapshots = new ArrayList<>();
        String cursor = null;
        do {
            MeetingOccurrenceRepository.PageResult<MeetingOccurrence> page =
                    occurrenceRepository.findByTenant(tid, null, null, cursor, 200);
            for (MeetingOccurrence occurrence : page.items()) {
                toSnapshot(tid, occurrence).ifPresent(snapshots::add);
            }
            cursor = page.nextCursor();
        } while (cursor != null && !cursor.isBlank());
        return List.copyOf(snapshots);
    }

    private Optional<OccurrenceIdentitySnapshot> toSnapshot(TenantId tenantId, MeetingOccurrence occurrence) {
        return seriesRepository.findByIdAndTenantId(occurrence.meetingSeriesId(), tenantId)
                .flatMap(series -> {
                    try {
                        return Optional.of(OccurrenceIdentityMapper.from(occurrence, series));
                    } catch (IllegalArgumentException ex) {
                        return Optional.empty();
                    }
                });
    }
}
