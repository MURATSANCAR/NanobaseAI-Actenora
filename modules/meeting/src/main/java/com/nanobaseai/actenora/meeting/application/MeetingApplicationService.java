package com.nanobaseai.actenora.meeting.application;

import com.nanobaseai.actenora.meeting.api.dto.ApplyAttendanceRequest;
import com.nanobaseai.actenora.meeting.api.dto.CreateMeetingRequest;
import com.nanobaseai.actenora.meeting.api.dto.CursorPageRequest;
import com.nanobaseai.actenora.meeting.api.dto.MeetingListResponse;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.meeting.api.dto.MeetingStatusTransitionRequest;
import com.nanobaseai.actenora.meeting.api.dto.ParticipantResponse;
import com.nanobaseai.actenora.meeting.api.dto.SyncInviteesRequest;
import com.nanobaseai.actenora.meeting.api.dto.UpdateMeetingRequest;
import com.nanobaseai.actenora.meeting.application.port.BusinessContextRepository;
import com.nanobaseai.actenora.meeting.application.port.ClockPort;
import com.nanobaseai.actenora.meeting.application.port.MeetingAuditPort;
import com.nanobaseai.actenora.meeting.application.port.MeetingEventPublisher;
import com.nanobaseai.actenora.meeting.application.port.MeetingOccurrenceRepository;
import com.nanobaseai.actenora.meeting.application.port.MeetingParticipantRepository;
import com.nanobaseai.actenora.meeting.application.port.MeetingQuotaPort;
import com.nanobaseai.actenora.meeting.application.port.MeetingSeriesRepository;
import com.nanobaseai.actenora.meeting.application.port.TenantContextPort;
import com.nanobaseai.actenora.meeting.domain.collaboration.UnauthorizedMeetingAccessException;
import com.nanobaseai.actenora.meeting.domain.exception.BusinessContextNotFoundException;
import com.nanobaseai.actenora.meeting.domain.exception.DuplicateGraphIdentityException;
import com.nanobaseai.actenora.meeting.domain.exception.DuplicateOccurrenceIdentityException;
import com.nanobaseai.actenora.meeting.domain.exception.MeetingNotFoundException;
import com.nanobaseai.actenora.meeting.domain.model.AttendanceStatus;
import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrence;
import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrenceStatus;
import com.nanobaseai.actenora.meeting.domain.model.MeetingParticipant;
import com.nanobaseai.actenora.meeting.domain.model.MeetingSeries;
import com.nanobaseai.actenora.meeting.domain.model.MeetingType;
import com.nanobaseai.actenora.meeting.domain.model.ParticipantType;
import com.nanobaseai.actenora.meeting.domain.model.ProcessingPriority;
import com.nanobaseai.actenora.meeting.domain.service.MeetingOccurrenceLifecyclePolicy;
import com.nanobaseai.actenora.sharedkernel.domain.DomainEvent;
import com.nanobaseai.actenora.sharedkernel.domain.PersonIdentityNormalizer;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MeetingApplicationService {

    private final TenantContextPort tenantContext;
    private final BusinessContextRepository businessContextRepository;
    private final MeetingSeriesRepository seriesRepository;
    private final MeetingOccurrenceRepository occurrenceRepository;
    private final MeetingParticipantRepository participantRepository;
    private final MeetingEventPublisher eventPublisher;
    private final MeetingAuditPort auditPort;
    private final MeetingQuotaPort meetingQuotaPort;
    private final ClockPort clock;

    public MeetingApplicationService(
            TenantContextPort tenantContext,
            BusinessContextRepository businessContextRepository,
            MeetingSeriesRepository seriesRepository,
            MeetingOccurrenceRepository occurrenceRepository,
            MeetingParticipantRepository participantRepository,
            MeetingEventPublisher eventPublisher,
            MeetingAuditPort auditPort,
            MeetingQuotaPort meetingQuotaPort,
            ClockPort clock
    ) {
        this.tenantContext = Objects.requireNonNull(tenantContext);
        this.businessContextRepository = Objects.requireNonNull(businessContextRepository);
        this.seriesRepository = Objects.requireNonNull(seriesRepository);
        this.occurrenceRepository = Objects.requireNonNull(occurrenceRepository);
        this.participantRepository = Objects.requireNonNull(participantRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.auditPort = Objects.requireNonNull(auditPort);
        this.meetingQuotaPort = Objects.requireNonNull(meetingQuotaPort);
        this.clock = Objects.requireNonNull(clock);
    }

    public MeetingResponse create(CreateMeetingRequest request) {
        TenantId tenantId = tenantContext.requireTenantId();
        UUID actor = tenantContext.requireActorUserId();
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(request.businessContextId(), "businessContextId");

        meetingQuotaPort.assertCanCreateMeeting(tenantId);

        businessContextRepository.findByIdAndTenantId(request.businessContextId(), tenantId)
                .orElseThrow(() -> new BusinessContextNotFoundException(request.businessContextId()));

        assertUniqueGraphIdentity(tenantId, request.graphEventImmutableId());
        Instant originalStart = request.originalStartAt() != null
                ? request.originalStartAt()
                : request.scheduledStartAt();
        assertUniqueOccurrenceIdentity(tenantId, request.icalUid(), originalStart);

        UUID seriesId = request.meetingSeriesId();
        if (seriesId == null
                && request.graphSeriesMasterId() != null
                && !request.graphSeriesMasterId().isBlank()) {
            seriesId = seriesRepository
                    .findByTenantIdAndGraphSeriesMasterId(tenantId, request.graphSeriesMasterId())
                    .map(MeetingSeries::id)
                    .orElse(null);
        }
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
                        normalizedDisplayName(input.displayName(), input.email()),
                        input.email(),
                        type,
                        input.external()
                );
                applyInviteResponse(participant, input.participantType());
                participantRepository.save(participant);
            }
        }

        eventPublisher.publishAll(events);
        meetingQuotaPort.recordMeetingCreated(tenantId);
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

    public Optional<MeetingResponse> findByGraphEventImmutableId(String graphEventImmutableId) {
        TenantId tenantId = tenantContext.requireTenantId();
        if (graphEventImmutableId == null || graphEventImmutableId.isBlank()) {
            return Optional.empty();
        }
        return occurrenceRepository.findByTenantIdAndGraphEventImmutableId(
                        tenantId, graphEventImmutableId.trim())
                .map(MeetingMapper::toResponse);
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

    public List<MeetingResponse> search(String query, MeetingOccurrenceStatus status, int limit) {
        TenantId tenantId = tenantContext.requireTenantId();
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("limit must be between 1 and 200");
        }
        return occurrenceRepository.searchByTitle(tenantId, query.trim(), status, limit).stream()
                .map(MeetingMapper::toResponse)
                .toList();
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

    /**
     * Applies time-based (or cancel) lifecycle hops for one occurrence.
     * Tenant context must already match the meeting's tenant.
     */
    public MeetingResponse advanceLifecycle(UUID id, boolean cancelled) {
        TenantId tenantId = tenantContext.requireTenantId();
        MeetingOccurrence current = requireOccurrence(id, tenantId);
        Instant now = clock.now();
        List<MeetingOccurrenceStatus> hops = MeetingOccurrenceLifecyclePolicy.nextHops(
                current.status(),
                current.scheduledStartAt(),
                current.scheduledEndAt(),
                now,
                cancelled
        );
        MeetingResponse latest = MeetingMapper.toResponse(current);
        for (MeetingOccurrenceStatus target : hops) {
            latest = transitionStatus(id, new MeetingStatusTransitionRequest(target, latest.version()));
        }
        return latest;
    }

    /**
     * Batch catch-up for DRAFT / past-start SCHEDULED / past-end IN_PROGRESS rows.
     * Caller must set tenant context per row via {@link #advanceLifecycleForOccurrence}.
     */
    public List<MeetingOccurrence> findDueForLifecycleAdvance(int limit) {
        return occurrenceRepository.findDueForLifecycleAdvance(clock.now(), Math.max(1, limit));
    }

    public List<ParticipantResponse> listParticipants(UUID meetingId) {
        TenantId tenantId = tenantContext.requireTenantId();
        requireOccurrence(meetingId, tenantId);
        return participantRepository.findByMeetingOccurrenceIdAndTenantId(meetingId, tenantId).stream()
                .map(MeetingMapper::toResponse)
                .toList();
    }

    /**
     * Upserts Graph calendar invitees by email. Existing attendance JOINED/LEFT/ABSENT is preserved;
     * RSVP (ACCEPTED/…) is applied when the role carries a {@code type|response} suffix.
     */
    public List<ParticipantResponse> syncInvitees(UUID meetingId, SyncInviteesRequest request) {
        TenantId tenantId = tenantContext.requireTenantId();
        UUID actor = tenantContext.requireActorUserId();
        requireOccurrence(meetingId, tenantId);
        Objects.requireNonNull(request, "request");
        List<CreateMeetingRequest.ParticipantInput> invitees = request.invitees();
        if (invitees.isEmpty()) {
            return listParticipants(meetingId);
        }

        List<MeetingParticipant> existing =
                new ArrayList<>(participantRepository.findByMeetingOccurrenceIdAndTenantId(meetingId, tenantId));
        int created = 0;
        int updated = 0;

        for (CreateMeetingRequest.ParticipantInput input : invitees) {
            String email = normalizeEmail(input.email());
            if (email == null) {
                continue;
            }
            ParticipantType type = parseParticipantType(input.participantType());
            MeetingParticipant match = findInviteeByEmail(existing, email);
            if (match == null) {
                match = findInviteeByDisplayName(existing, input.displayName(), email);
            }
            if (match != null) {
                if (input.displayName() != null && !input.displayName().isBlank()) {
                    match.rename(normalizedDisplayName(input.displayName(), email));
                }
                match.assignParticipantType(type);
                match.linkEmail(email);
                applyInviteResponse(match, input.participantType());
                participantRepository.save(match);
                updated++;
            } else {
                boolean external = input.external();
                String entra = input.entraUserId();
                if (!external && (entra == null || entra.isBlank())) {
                    // Graph calendar attendees often lack OID; email is a stable internal key (same as create path).
                    entra = email;
                }
                MeetingParticipant participant = MeetingParticipant.create(
                        tenantId,
                        meetingId,
                        entra,
                        normalizedDisplayName(input.displayName(), email),
                        email,
                        type,
                        external
                );
                applyInviteResponse(participant, input.participantType());
                participantRepository.save(participant);
                existing.add(participant);
                created++;
            }
        }

        auditPort.record(tenantId, actor, "MEETING_INVITEES_SYNCED", "MeetingOccurrence", meetingId,
                Map.of("invitees", invitees.size(), "created", created, "updated", updated));
        return participantRepository.findByMeetingOccurrenceIdAndTenantId(meetingId, tenantId).stream()
                .map(MeetingMapper::toResponse)
                .toList();
    }

    private static MeetingParticipant findInviteeByEmail(List<MeetingParticipant> existing, String email) {
        for (MeetingParticipant participant : existing) {
            if (email.equals(normalizeEmail(participant.email()))) {
                return participant;
            }
        }
        return null;
    }

    public List<ParticipantResponse> applyAttendance(UUID meetingId, ApplyAttendanceRequest request) {
        TenantId tenantId = tenantContext.requireTenantId();
        UUID actor = tenantContext.requireActorUserId();
        requireOccurrence(meetingId, tenantId);
        Objects.requireNonNull(request, "request");
        List<ApplyAttendanceRequest.AttendanceRecord> attended =
                request.attended() == null ? List.of() : request.attended();

        List<MeetingParticipant> existing =
                new ArrayList<>(participantRepository.findByMeetingOccurrenceIdAndTenantId(meetingId, tenantId));
        Set<UUID> matchedIds = new HashSet<>();

        for (ApplyAttendanceRequest.AttendanceRecord record : attended) {
            MeetingParticipant match = findMatchingParticipant(existing, record);
            if (match != null) {
                match.markJoined(record.joinedAt(), record.leftAt());
                promoteIfOrganizerRole(match, record.role());
                match.linkEntraUserId(record.entraUserId());
                match.linkEmail(record.email());
                participantRepository.save(match);
                matchedIds.add(match.id());
            } else if (normalizeEmail(record.email()) != null
                    || normalizeDisplay(record.displayName()) != null) {
                MeetingParticipant created = createFromAttendance(tenantId, meetingId, record);
                participantRepository.save(created);
                existing.add(created);
                matchedIds.add(created.id());
            }
        }

        if (request.markMissingAsAbsent()) {
            for (MeetingParticipant participant : existing) {
                if (!matchedIds.contains(participant.id())) {
                    participant.markAbsent();
                    participantRepository.save(participant);
                }
            }
        }

        auditPort.record(tenantId, actor, "MEETING_ATTENDANCE_SYNCED", "MeetingOccurrence", meetingId,
                Map.of("attended", attended.size(), "markMissingAsAbsent", request.markMissingAsAbsent()));
        return participantRepository.findByMeetingOccurrenceIdAndTenantId(meetingId, tenantId).stream()
                .map(MeetingMapper::toResponse)
                .toList();
    }

    private static MeetingParticipant findMatchingParticipant(
            List<MeetingParticipant> existing,
            ApplyAttendanceRequest.AttendanceRecord record
    ) {
        String email = normalizeEmail(record.email());
        String entra = normalizeId(record.entraUserId());
        String display = resolvedDisplayKey(existing, record.displayName());

        // 1) Prefer email — calendar invitees are keyed by mail.
        if (email != null) {
            for (MeetingParticipant participant : existing) {
                if (email.equals(normalizeEmail(participant.email()))) {
                    return participant;
                }
            }
        }
        // 2) Real Entra object id (after prior backfill, or when calendar stored OID).
        if (entra != null) {
            for (MeetingParticipant participant : existing) {
                if (entra.equalsIgnoreCase(normalizeId(participant.entraUserId()))) {
                    return participant;
                }
            }
        }
        // 3) Organizer role often arrives without email; match the single ORGANIZER row by role/name.
        if (isOrganizerRole(record.role())) {
            List<MeetingParticipant> organizers = existing.stream()
                    .filter(p -> p.participantType() == ParticipantType.ORGANIZER)
                    .toList();
            if (organizers.size() == 1) {
                return organizers.getFirst();
            }
            if (display != null) {
                for (MeetingParticipant organizer : organizers) {
                    if (display.equals(normalizeDisplay(organizer.displayName()))) {
                        return organizer;
                    }
                }
            }
        }
        // 4) Unique display name only — ambiguous names return null (do not guess).
        if (display != null) {
            MeetingParticipant byName = null;
            for (MeetingParticipant participant : existing) {
                if (display.equals(normalizeDisplay(participant.displayName()))) {
                    if (byName != null) {
                        return null;
                    }
                    byName = participant;
                }
            }
            return byName;
        }
        return null;
    }

    private static String normalizeDisplay(String name) {
        String key = PersonIdentityNormalizer.identityKey(name);
        return key.isBlank() ? null : key;
    }

    private static MeetingParticipant findInviteeByDisplayName(
            List<MeetingParticipant> existing,
            String displayName,
            String email
    ) {
        String key = resolvedDisplayKey(existing, displayName);
        if (key == null) {
            key = resolvedDisplayKey(existing, email);
        }
        if (key == null) {
            return null;
        }
        MeetingParticipant found = null;
        for (MeetingParticipant participant : existing) {
            if (!key.equals(normalizeDisplay(participant.displayName()))) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = participant;
        }
        return found;
    }

    private static String resolvedDisplayKey(
            List<MeetingParticipant> existing,
            String candidate
    ) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        List<String> roster = existing.stream()
                .map(MeetingParticipant::displayName)
                .toList();
        return PersonIdentityNormalizer.resolveUnique(candidate, roster)
                .map(PersonIdentityNormalizer::identityKey)
                .filter(key -> !key.isBlank())
                .orElseGet(() -> normalizeDisplay(candidate));
    }

    private static MeetingParticipant createFromAttendance(
            TenantId tenantId,
            UUID meetingId,
            ApplyAttendanceRequest.AttendanceRecord record
    ) {
        String email = normalizeEmail(record.email());
        String entra = normalizeId(record.entraUserId());
        boolean external = entra == null || entra.isBlank();
        String display = normalizedDisplayName(record.displayName(), email);
        ParticipantType type = isOrganizerRole(record.role())
                ? ParticipantType.ORGANIZER
                : ParticipantType.REQUIRED;
        MeetingParticipant created = MeetingParticipant.create(
                tenantId,
                meetingId,
                external ? null : entra,
                display,
                email,
                type,
                external
        );
        created.markJoined(record.joinedAt(), record.leftAt());
        return created;
    }

    private static String normalizedDisplayName(String displayName, String email) {
        String display = PersonIdentityNormalizer.displayName(displayName);
        if (display.isBlank()) {
            display = PersonIdentityNormalizer.displayName(email);
        }
        if (display.isBlank()) {
            throw new IllegalArgumentException("participant displayName or email is required");
        }
        return display;
    }

    private static void promoteIfOrganizerRole(MeetingParticipant participant, String role) {
        if (isOrganizerRole(role)) {
            participant.promoteToOrganizer();
        }
    }

    private static boolean isOrganizerRole(String role) {
        if (role == null) {
            return false;
        }
        String base = role.contains("|") ? role.substring(0, role.indexOf('|')) : role;
        return "organizer".equalsIgnoreCase(base.trim());
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return id.trim();
    }

    private MeetingOccurrence requireOccurrence(UUID id, TenantId tenantId) {
        Optional<MeetingOccurrence> owned = occurrenceRepository.findByIdAndTenantId(id, tenantId);
        if (owned.isPresent()) {
            return owned.get();
        }
        if (occurrenceRepository.existsById(id)) {
            throw new UnauthorizedMeetingAccessException(id);
        }
        throw new MeetingNotFoundException(id);
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
        String typePart = value.contains("|") ? value.substring(0, value.indexOf('|')) : value;
        String normalized = typePart.trim().toUpperCase(Locale.ROOT);
        if ("ATTENDEE".equals(normalized)) {
            return ParticipantType.REQUIRED;
        }
        if ("ORGANIZER".equals(normalized)) {
            return ParticipantType.ORGANIZER;
        }
        if ("OPTIONAL".equals(normalized)) {
            return ParticipantType.OPTIONAL;
        }
        if ("PRESENTER".equals(normalized)) {
            return ParticipantType.PRESENTER;
        }
        if ("REQUIRED".equals(normalized)) {
            return ParticipantType.REQUIRED;
        }
        try {
            return ParticipantType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return ParticipantType.REQUIRED;
        }
    }

    private static void applyInviteResponse(MeetingParticipant participant, String roleWithResponse) {
        if (roleWithResponse == null || !roleWithResponse.contains("|")) {
            return;
        }
        String response = roleWithResponse.substring(roleWithResponse.indexOf('|') + 1).trim().toUpperCase(Locale.ROOT);
        switch (response) {
            case "ACCEPTED" -> participant.applyInviteResponse(AttendanceStatus.ACCEPTED);
            case "DECLINED" -> participant.applyInviteResponse(AttendanceStatus.DECLINED);
            case "TENTATIVELYACCEPTED", "TENTATIVE" -> participant.applyInviteResponse(AttendanceStatus.TENTATIVE);
            case "NOTRESPONDED", "NONE" -> participant.applyInviteResponse(AttendanceStatus.INVITED);
            default -> {
            }
        }
    }
}
