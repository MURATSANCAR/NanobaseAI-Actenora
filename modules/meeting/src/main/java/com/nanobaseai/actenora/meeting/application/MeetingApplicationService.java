package com.nanobaseai.actenora.meeting.application;

import com.nanobaseai.actenora.meeting.api.dto.CreateMeetingRequest;
import com.nanobaseai.actenora.meeting.api.dto.CursorPageRequest;
import com.nanobaseai.actenora.meeting.api.dto.MeetingListResponse;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.meeting.api.dto.MeetingStatusTransitionRequest;
import com.nanobaseai.actenora.meeting.api.dto.ParticipantResponse;
import com.nanobaseai.actenora.meeting.api.dto.UpdateMeetingRequest;
import com.nanobaseai.actenora.meeting.application.port.BusinessContextRepository;
import com.nanobaseai.actenora.meeting.application.port.ClockPort;
import com.nanobaseai.actenora.meeting.application.port.MeetingAuditPort;
import com.nanobaseai.actenora.meeting.application.port.MeetingEventPublisher;
import com.nanobaseai.actenora.meeting.application.port.MeetingOccurrenceRepository;
import com.nanobaseai.actenora.meeting.application.port.MeetingParticipantRepository;
import com.nanobaseai.actenora.meeting.application.port.MeetingSeriesRepository;
import com.nanobaseai.actenora.meeting.application.port.TenantContextPort;
import com.nanobaseai.actenora.meeting.domain.exception.BusinessContextNotFoundException;
import com.nanobaseai.actenora.meeting.domain.exception.DuplicateGraphIdentityException;
import com.nanobaseai.actenora.meeting.domain.exception.DuplicateOccurrenceIdentityException;
import com.nanobaseai.actenora.meeting.domain.exception.MeetingNotFoundException;
import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrence;
import com.nanobaseai.actenora.meeting.domain.model.MeetingParticipant;
import com.nanobaseai.actenora.meeting.domain.model.MeetingSeries;
import com.nanobaseai.actenora.meeting.domain.model.MeetingType;
import com.nanobaseai.actenora.meeting.domain.model.ParticipantType;
import com.nanobaseai.actenora.meeting.domain.model.ProcessingPriority;
import com.nanobaseai.actenora.sharedkernel.domain.DomainEvent;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class MeetingApplicationService {

    private final TenantContextPort tenantContext;
    private final BusinessContextRepository businessContextRepository;
    private final MeetingSeriesRepository seriesRepository;
    private final MeetingOccurrenceRepository occurrenceRepository;
    private final MeetingParticipantRepository participantRepository;
    private final MeetingEventPublisher eventPublisher;
    private final MeetingAuditPort auditPort;
    private final ClockPort clock;

    public MeetingApplicationService(
            TenantContextPort tenantContext,
            BusinessContextRepository businessContextRepository,
            MeetingSeriesRepository seriesRepository,
            MeetingOccurrenceRepository occurrenceRepository,
            MeetingParticipantRepository participantRepository,
            MeetingEventPublisher eventPublisher,
            MeetingAuditPort auditPort,
            ClockPort clock
    ) {
        this.tenantContext = Objects.requireNonNull(tenantContext);
        this.businessContextRepository = Objects.requireNonNull(businessContextRepository);
        this.seriesRepository = Objects.requireNonNull(seriesRepository);
        this.occurrenceRepository = Objects.requireNonNull(occurrenceRepository);
        this.participantRepository = Objects.requireNonNull(participantRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.auditPort = Objects.requireNonNull(auditPort);
        this.clock = Objects.requireNonNull(clock);
    }

    public MeetingResponse create(CreateMeetingRequest request) {
        TenantId tenantId = tenantContext.requireTenantId();
        UUID actor = tenantContext.requireActorUserId();
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(request.businessContextId(), "businessContextId");

        businessContextRepository.findByIdAndTenantId(request.businessContextId(), tenantId)
                .orElseThrow(() -> new BusinessContextNotFoundException(request.businessContextId()));

        assertUniqueGraphIdentity(tenantId, request.graphEventImmutableId());
        Instant originalStart = request.originalStartAt() != null
                ? request.originalStartAt()
                : request.scheduledStartAt();
        assertUniqueOccurrenceIdentity(tenantId, request.icalUid(), originalStart);

        UUID seriesId = request.meetingSeriesId();
        if (seriesId == null) {
            MeetingSeries series = MeetingSeries.create(
                    tenantId,
                    request.businessContextId(),
                    request.graphSeriesMasterId(),
                    actor,
                    request.title(),
                    request.meetingType() == null ? MeetingType.STANDALONE : request.meetingType(),
                    clock.now()
            );
            seriesId = seriesRepository.save(series).id();
        } else {
            UUID requestedSeriesId = seriesId;
            seriesRepository.findByIdAndTenantId(requestedSeriesId, tenantId)
                    .orElseThrow(() -> new MeetingNotFoundException(requestedSeriesId));
        }

        MeetingOccurrence occurrence = MeetingOccurrence.create(
                tenantId,
                seriesId,
                request.businessContextId(),
                request.graphEventImmutableId(),
                request.icalUid(),
                originalStart,
                request.teamsMeetingId(),
                request.chatId(),
                request.joinWebUrl(),
                request.title(),
                actor,
                request.scheduledStartAt(),
                request.scheduledEndAt(),
                request.processingPriority() == null ? ProcessingPriority.NORMAL : request.processingPriority(),
                clock.now()
        );

        MeetingOccurrence saved = occurrenceRepository.save(occurrence);
        List<DomainEvent> events = new ArrayList<>(saved.pullDomainEvents());

        if (request.participants() != null) {
            for (CreateMeetingRequest.ParticipantInput input : request.participants()) {
                ParticipantType type = parseParticipantType(input.participantType());
                MeetingParticipant participant = MeetingParticipant.create(
                        tenantId,
                        saved.id(),
                        input.entraUserId(),
                        input.displayName(),
                        input.email(),
                        type,
                        input.external()
                );
                participantRepository.save(participant);
            }
        }

        eventPublisher.publishAll(events);
        auditPort.record(tenantId, actor, "MEETING_CREATED", "MeetingOccurrence", saved.id(),
                Map.of("title", saved.title(), "status", saved.status().name()));
        return MeetingMapper.toResponse(saved);
    }

    public MeetingResponse update(UUID id, UpdateMeetingRequest request) {
        TenantId tenantId = tenantContext.requireTenantId();
        UUID actor = tenantContext.requireActorUserId();
        MeetingOccurrence existing = requireOccurrence(id, tenantId);

        if (request.graphEventImmutableId() != null
                && !request.graphEventImmutableId().equals(existing.graphEventImmutableId())) {
            assertUniqueGraphIdentity(tenantId, request.graphEventImmutableId());
        }
        Instant nextOriginal = request.originalStartAt() != null
                ? request.originalStartAt()
                : existing.originalStartAt();
        String nextIcal = request.icalUid() != null ? request.icalUid() : existing.icalUid();
        if ((request.icalUid() != null || request.originalStartAt() != null)
                && nextIcal != null
                && !(Objects.equals(nextIcal, existing.icalUid())
                && Objects.equals(nextOriginal, existing.originalStartAt()))) {
            assertUniqueOccurrenceIdentity(tenantId, nextIcal, nextOriginal);
        }

        existing.update(
                request.title(),
                request.scheduledStartAt(),
                request.scheduledEndAt(),
                request.graphEventImmutableId(),
                request.icalUid(),
                request.originalStartAt(),
                request.teamsMeetingId(),
                request.chatId(),
                request.joinWebUrl(),
                request.processingPriority(),
                request.expectedVersion(),
                clock.now()
        );

        MeetingOccurrence saved = occurrenceRepository.save(existing);
        eventPublisher.publishAll(saved.pullDomainEvents());
        auditPort.record(tenantId, actor, "MEETING_UPDATED", "MeetingOccurrence", saved.id(),
                Map.of("version", saved.version()));
        return MeetingMapper.toResponse(saved);
    }

    public MeetingResponse detail(UUID id) {
        TenantId tenantId = tenantContext.requireTenantId();
        return MeetingMapper.toResponse(requireOccurrence(id, tenantId));
    }

    public MeetingListResponse list(CursorPageRequest pageRequest) {
        TenantId tenantId = tenantContext.requireTenantId();
        CursorPageRequest safe = pageRequest == null
                ? new CursorPageRequest(null, null, null, 20)
                : pageRequest;
        MeetingOccurrenceRepository.PageResult<MeetingOccurrence> page = occurrenceRepository.findByTenant(
                tenantId,
                safe.status(),
                safe.businessContextId(),
                safe.cursor(),
                safe.pageSize()
        );
        return new MeetingListResponse(
                page.items().stream().map(MeetingMapper::toResponse).toList(),
                page.nextCursor()
        );
    }

    public MeetingResponse transitionStatus(UUID id, MeetingStatusTransitionRequest request) {
        TenantId tenantId = tenantContext.requireTenantId();
        UUID actor = tenantContext.requireActorUserId();
        MeetingOccurrence existing = requireOccurrence(id, tenantId);
        existing.transitionTo(request.targetStatus(), request.expectedVersion(), clock.now());
        MeetingOccurrence saved = occurrenceRepository.save(existing);
        eventPublisher.publishAll(saved.pullDomainEvents());
        auditPort.record(tenantId, actor, "MEETING_STATUS_TRANSITION", "MeetingOccurrence", saved.id(),
                Map.of("status", saved.status().name(), "version", saved.version()));
        return MeetingMapper.toResponse(saved);
    }

    public List<ParticipantResponse> listParticipants(UUID meetingId) {
        TenantId tenantId = tenantContext.requireTenantId();
        requireOccurrence(meetingId, tenantId);
        return participantRepository.findByMeetingOccurrenceIdAndTenantId(meetingId, tenantId).stream()
                .map(MeetingMapper::toResponse)
                .toList();
    }

    private MeetingOccurrence requireOccurrence(UUID id, TenantId tenantId) {
        return occurrenceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new MeetingNotFoundException(id));
    }

    private void assertUniqueGraphIdentity(TenantId tenantId, String graphEventImmutableId) {
        if (graphEventImmutableId == null || graphEventImmutableId.isBlank()) {
            return;
        }
        if (occurrenceRepository.existsByTenantIdAndGraphEventImmutableId(tenantId, graphEventImmutableId.trim())) {
            throw new DuplicateGraphIdentityException(graphEventImmutableId.trim());
        }
    }

    private void assertUniqueOccurrenceIdentity(TenantId tenantId, String icalUid, Instant originalStartAt) {
        if (icalUid == null || icalUid.isBlank() || originalStartAt == null) {
            return;
        }
        if (occurrenceRepository.existsByTenantIdAndIcalUidAndOriginalStartAt(
                tenantId, icalUid.trim(), originalStartAt)) {
            throw new DuplicateOccurrenceIdentityException(icalUid.trim());
        }
    }

    private static ParticipantType parseParticipantType(String value) {
        if (value == null || value.isBlank()) {
            return ParticipantType.REQUIRED;
        }
        return ParticipantType.valueOf(value.trim().toUpperCase());
    }
}
