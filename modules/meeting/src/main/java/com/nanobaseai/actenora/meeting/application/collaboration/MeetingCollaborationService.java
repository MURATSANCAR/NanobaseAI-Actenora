package com.nanobaseai.actenora.meeting.application.collaboration;

import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.AgendaResponse;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.CreateMarkerRequest;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.CreateOpenTaskRequest;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.MarkerResponse;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.MeetingWorkspaceResponse;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.OpenTaskResponse;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.PrivateNoteResponse;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.SharedNoteResponse;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.UpdateAgendaRequest;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.UpsertPrivateNoteRequest;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.UpsertSharedNoteRequest;
import com.nanobaseai.actenora.meeting.api.collaboration.MeetingCollaborationApi;
import com.nanobaseai.actenora.meeting.application.collaboration.port.CollaborationIdempotencyStore;
import com.nanobaseai.actenora.meeting.application.collaboration.port.MeetingAgendaRepository;
import com.nanobaseai.actenora.meeting.application.collaboration.port.MeetingMarkerRepository;
import com.nanobaseai.actenora.meeting.application.collaboration.port.OpenTaskRepository;
import com.nanobaseai.actenora.meeting.application.collaboration.port.PrivateNoteRepository;
import com.nanobaseai.actenora.meeting.application.collaboration.port.SharedNoteRepository;
import com.nanobaseai.actenora.meeting.application.port.ClockPort;
import com.nanobaseai.actenora.meeting.application.port.TenantContextPort;
import com.nanobaseai.actenora.meeting.domain.collaboration.MarkerOffsetCalculator;
import com.nanobaseai.actenora.meeting.domain.collaboration.MeetingAgenda;
import com.nanobaseai.actenora.meeting.domain.collaboration.MeetingMarker;
import com.nanobaseai.actenora.meeting.domain.collaboration.OpenTask;
import com.nanobaseai.actenora.meeting.domain.collaboration.PrivateNote;
import com.nanobaseai.actenora.meeting.domain.collaboration.PrivateNoteAccessDeniedException;
import com.nanobaseai.actenora.meeting.domain.collaboration.PrivateNoteAccessPolicy;
import com.nanobaseai.actenora.meeting.domain.collaboration.PrivateNoteAiAccessDeniedException;
import com.nanobaseai.actenora.meeting.domain.collaboration.SharedNote;
import com.nanobaseai.actenora.meeting.domain.exception.MeetingNotFoundException;
import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrence;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MeetingCollaborationService implements MeetingCollaborationApi {

    private final TenantContextPort tenantContext;
    private final ClockPort clock;
    private final MeetingMembershipGuard membershipGuard;
    private final MeetingMarkerRepository markerRepository;
    private final SharedNoteRepository sharedNoteRepository;
    private final PrivateNoteRepository privateNoteRepository;
    private final MeetingAgendaRepository agendaRepository;
    private final OpenTaskRepository openTaskRepository;
    private final CollaborationIdempotencyStore idempotencyStore;

    public MeetingCollaborationService(
            TenantContextPort tenantContext,
            ClockPort clock,
            MeetingMembershipGuard membershipGuard,
            MeetingMarkerRepository markerRepository,
            SharedNoteRepository sharedNoteRepository,
            PrivateNoteRepository privateNoteRepository,
            MeetingAgendaRepository agendaRepository,
            OpenTaskRepository openTaskRepository,
            CollaborationIdempotencyStore idempotencyStore
    ) {
        this.tenantContext = Objects.requireNonNull(tenantContext);
        this.clock = Objects.requireNonNull(clock);
        this.membershipGuard = Objects.requireNonNull(membershipGuard);
        this.markerRepository = Objects.requireNonNull(markerRepository);
        this.sharedNoteRepository = Objects.requireNonNull(sharedNoteRepository);
        this.privateNoteRepository = Objects.requireNonNull(privateNoteRepository);
        this.agendaRepository = Objects.requireNonNull(agendaRepository);
        this.openTaskRepository = Objects.requireNonNull(openTaskRepository);
        this.idempotencyStore = Objects.requireNonNull(idempotencyStore);
    }

    @Override
    public MeetingWorkspaceResponse getWorkspace(UUID meetingOccurrenceId) {
        TenantId tenantId = tenantContext.requireTenantId();
        UUID actor = tenantContext.requireActorUserId();
        MeetingOccurrence meeting = membershipGuard.requireMemberMeeting(tenantId, meetingOccurrenceId, actor);

        AgendaResponse agenda = agendaRepository.findByMeetingOccurrenceIdAndTenantId(meeting.id(), tenantId)
                .map(this::toAgenda)
                .orElse(null);
        List<OpenTaskResponse> tasks = listOpenTasksInternal(meeting.id(), tenantId);
        List<MarkerResponse> markers = listMarkersInternal(meeting.id(), tenantId);
        SharedNoteResponse shared = sharedNoteRepository.findByMeetingOccurrenceIdAndTenantId(meeting.id(), tenantId)
                .map(this::toShared)
                .orElse(null);
        PrivateNoteResponse priv = privateNoteRepository
                .findByMeetingOccurrenceIdAndOwnerAndTenantId(meeting.id(), actor, tenantId)
                .map(this::toPrivate)
                .orElse(null);
        return new MeetingWorkspaceResponse(meeting.id(), agenda, tasks, markers, shared, priv);
    }

    @Override
    public MarkerResponse createMarker(UUID meetingOccurrenceId, CreateMarkerRequest request, String idempotencyKey) {
        TenantId tenantId = tenantContext.requireTenantId();
        UUID actor = tenantContext.requireActorUserId();
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(request.type(), "type");

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<MeetingMarker> existing = markerRepository.findByTenantAndIdempotencyKey(
                    tenantId, actor, idempotencyKey.trim()
            );
            if (existing.isPresent()) {
                return toMarker(existing.get());
            }
            Optional<String> cached = idempotencyStore.findResponseJson(tenantId, actor, idempotencyKey.trim());
            if (cached.isPresent()) {
                // Prefer entity lookup; if only JSON cache present, recreate is avoided by key store on marker.
                existing = markerRepository.findByTenantAndIdempotencyKey(tenantId, actor, idempotencyKey.trim());
                if (existing.isPresent()) {
                    return toMarker(existing.get());
                }
            }
        }

        MeetingOccurrence meeting = membershipGuard.requireMemberMeeting(tenantId, meetingOccurrenceId, actor);
        Instant serverNow = clock.now();
        Instant anchor = MarkerOffsetCalculator.resolveAnchor(meeting.actualStartAt(), meeting.scheduledStartAt());
        long offsetMs = MarkerOffsetCalculator.offsetMs(anchor, serverNow);

        MeetingMarker marker = MeetingMarker.create(
                tenantId,
                meeting.id(),
                request.type(),
                request.body(),
                offsetMs,
                actor,
                serverNow,
                idempotencyKey
        );
        MeetingMarker saved = markerRepository.save(marker);
        MarkerResponse response = toMarker(saved);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyStore.putResponseJson(tenantId, actor, idempotencyKey.trim(), saved.id().toString());
        }
        return response;
    }

    @Override
    public List<MarkerResponse> listMarkers(UUID meetingOccurrenceId) {
        TenantId tenantId = tenantContext.requireTenantId();
        UUID actor = tenantContext.requireActorUserId();
        membershipGuard.requireMemberMeeting(tenantId, meetingOccurrenceId, actor);
        return listMarkersInternal(meetingOccurrenceId, tenantId);
    }

    @Override
    public SharedNoteResponse upsertSharedNote(UUID meetingOccurrenceId, UpsertSharedNoteRequest request) {
        TenantId tenantId = tenantContext.requireTenantId();
        UUID actor = tenantContext.requireActorUserId();
        Objects.requireNonNull(request, "request");
        MeetingOccurrence meeting = membershipGuard.requireMemberMeeting(tenantId, meetingOccurrenceId, actor);
        Instant now = clock.now();

        Optional<SharedNote> existing = sharedNoteRepository.findByMeetingOccurrenceIdAndTenantId(meeting.id(), tenantId);
        if (existing.isEmpty()) {
            SharedNote created = SharedNote.create(tenantId, meeting.id(), request.body(), actor, now);
            return toShared(sharedNoteRepository.save(created));
        }
        SharedNote note = existing.get();
        long expected = request.expectedVersion() == null ? note.version() : request.expectedVersion();
        note.updateBody(request.body(), actor, now, expected);
        return toShared(sharedNoteRepository.save(note));
    }

    @Override
    public SharedNoteResponse getSharedNote(UUID meetingOccurrenceId) {
        TenantId tenantId = tenantContext.requireTenantId();
        UUID actor = tenantContext.requireActorUserId();
        membershipGuard.requireMemberMeeting(tenantId, meetingOccurrenceId, actor);
        return sharedNoteRepository.findByMeetingOccurrenceIdAndTenantId(meetingOccurrenceId, tenantId)
                .map(this::toShared)
                .orElse(null);
    }

    @Override
    public PrivateNoteResponse upsertPrivateNote(UUID meetingOccurrenceId, UpsertPrivateNoteRequest request) {
        TenantId tenantId = tenantContext.requireTenantId();
        UUID actor = tenantContext.requireActorUserId();
        Objects.requireNonNull(request, "request");
        MeetingOccurrence meeting = membershipGuard.requireMemberMeeting(tenantId, meetingOccurrenceId, actor);
        Instant now = clock.now();

        Optional<PrivateNote> existing = privateNoteRepository
                .findByMeetingOccurrenceIdAndOwnerAndTenantId(meeting.id(), actor, tenantId);
        if (existing.isEmpty()) {
            PrivateNote created = PrivateNote.create(tenantId, meeting.id(), actor, request.body(), now);
            return toPrivate(privateNoteRepository.save(created));
        }
        PrivateNote note = existing.get();
        long expected = request.expectedVersion() == null ? note.version() : request.expectedVersion();
        note.updateBody(request.body(), actor, now, expected);
        return toPrivate(privateNoteRepository.save(note));
    }

    @Override
    public PrivateNoteResponse getOwnPrivateNote(UUID meetingOccurrenceId) {
        TenantId tenantId = tenantContext.requireTenantId();
        UUID actor = tenantContext.requireActorUserId();
        MeetingOccurrence meeting = membershipGuard.requireMemberMeeting(tenantId, meetingOccurrenceId, actor);
        return privateNoteRepository
                .findByMeetingOccurrenceIdAndOwnerAndTenantId(meeting.id(), actor, tenantId)
                .map(this::toPrivate)
                .orElse(null);
    }

    @Override
    public PrivateNoteResponse getPrivateNoteById(UUID noteId) {
        TenantId tenantId = tenantContext.requireTenantId();
        UUID actor = tenantContext.requireActorUserId();
        PrivateNote note = privateNoteRepository.findByIdAndTenantId(noteId, tenantId)
                .orElseThrow(() -> new MeetingNotFoundException(noteId));
        membershipGuard.requireMemberMeeting(tenantId, note.meetingOccurrenceId(), actor);
        MeetingOccurrence meeting = membershipGuard.requireMemberMeeting(
                tenantId, note.meetingOccurrenceId(), actor
        );
        boolean isOrganizer = meeting.organizerUserId().equals(actor);
        if (!PrivateNoteAccessPolicy.canHumanRead(note, actor, isOrganizer, false)) {
            throw new PrivateNoteAccessDeniedException(note.id());
        }
        return toPrivate(note);
    }

    @Override
    public PrivateNoteResponse grantPrivateNoteAiUse(UUID noteId) {
        TenantId tenantId = tenantContext.requireTenantId();
        UUID actor = tenantContext.requireActorUserId();
        PrivateNote note = privateNoteRepository.findByIdAndTenantId(noteId, tenantId)
                .orElseThrow(() -> new MeetingNotFoundException(noteId));
        membershipGuard.requireMemberMeeting(tenantId, note.meetingOccurrenceId(), actor);
        note.grantAiUse(actor, clock.now());
        return toPrivate(privateNoteRepository.save(note));
    }

    @Override
    public PrivateNoteResponse readPrivateNoteForAi(UUID noteId) {
        TenantId tenantId = tenantContext.requireTenantId();
        PrivateNote note = privateNoteRepository.findByIdAndTenantId(noteId, tenantId)
                .orElseThrow(() -> new MeetingNotFoundException(noteId));
        if (!PrivateNoteAccessPolicy.canAiUse(note)) {
            throw new PrivateNoteAiAccessDeniedException(noteId);
        }
        return toPrivate(note);
    }

    @Override
    public AgendaResponse updateAgenda(UUID meetingOccurrenceId, UpdateAgendaRequest request, String idempotencyKey) {
        TenantId tenantId = tenantContext.requireTenantId();
        UUID actor = tenantContext.requireActorUserId();
        Objects.requireNonNull(request, "request");

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<String> cached = idempotencyStore.findResponseJson(tenantId, actor, idempotencyKey.trim());
            if (cached.isPresent()) {
                Optional<MeetingAgenda> agenda = agendaRepository.findByMeetingOccurrenceIdAndTenantId(
                        meetingOccurrenceId, tenantId
                );
                if (agenda.isPresent()) {
                    return toAgenda(agenda.get());
                }
            }
        }

        MeetingOccurrence meeting = membershipGuard.requireMemberMeeting(tenantId, meetingOccurrenceId, actor);
        Instant now = clock.now();
        Optional<MeetingAgenda> existing = agendaRepository.findByMeetingOccurrenceIdAndTenantId(meeting.id(), tenantId);
        MeetingAgenda saved;
        if (existing.isEmpty()) {
            saved = agendaRepository.save(MeetingAgenda.create(tenantId, meeting.id(), request.items(), actor, now));
        } else {
            MeetingAgenda agenda = existing.get();
            long expected = request.expectedVersion() == null ? agenda.version() : request.expectedVersion();
            agenda.replaceItems(request.items(), actor, now, expected);
            saved = agendaRepository.save(agenda);
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyStore.putResponseJson(tenantId, actor, idempotencyKey.trim(), saved.id().toString());
        }
        return toAgenda(saved);
    }

    @Override
    public AgendaResponse getAgenda(UUID meetingOccurrenceId) {
        TenantId tenantId = tenantContext.requireTenantId();
        UUID actor = tenantContext.requireActorUserId();
        membershipGuard.requireMemberMeeting(tenantId, meetingOccurrenceId, actor);
        return agendaRepository.findByMeetingOccurrenceIdAndTenantId(meetingOccurrenceId, tenantId)
                .map(this::toAgenda)
                .orElse(null);
    }

    @Override
    public OpenTaskResponse createOpenTask(UUID meetingOccurrenceId, CreateOpenTaskRequest request) {
        TenantId tenantId = tenantContext.requireTenantId();
        UUID actor = tenantContext.requireActorUserId();
        Objects.requireNonNull(request, "request");
        MeetingOccurrence meeting = membershipGuard.requireMemberMeeting(tenantId, meetingOccurrenceId, actor);
        OpenTask task = OpenTask.create(
                tenantId,
                meeting.id(),
                request.title(),
                request.assigneeUserId(),
                actor,
                clock.now(),
                request.sourceMeetingOccurrenceId()
        );
        return toTask(openTaskRepository.save(task));
    }

    @Override
    public List<OpenTaskResponse> listOpenTasks(UUID meetingOccurrenceId) {
        TenantId tenantId = tenantContext.requireTenantId();
        UUID actor = tenantContext.requireActorUserId();
        membershipGuard.requireMemberMeeting(tenantId, meetingOccurrenceId, actor);
        return listOpenTasksInternal(meetingOccurrenceId, tenantId);
    }

    private List<MarkerResponse> listMarkersInternal(UUID meetingOccurrenceId, TenantId tenantId) {
        return markerRepository.findByMeetingOccurrenceIdAndTenantId(meetingOccurrenceId, tenantId).stream()
                .map(this::toMarker)
                .toList();
    }

    private List<OpenTaskResponse> listOpenTasksInternal(UUID meetingOccurrenceId, TenantId tenantId) {
        return openTaskRepository.findOpenByMeetingOccurrenceIdAndTenantId(meetingOccurrenceId, tenantId).stream()
                .map(this::toTask)
                .toList();
    }

    private MarkerResponse toMarker(MeetingMarker marker) {
        return new MarkerResponse(
                marker.id(),
                marker.meetingOccurrenceId(),
                marker.type(),
                marker.body(),
                marker.offsetMs(),
                marker.createdByUserId(),
                marker.createdAt()
        );
    }

    private SharedNoteResponse toShared(SharedNote note) {
        return new SharedNoteResponse(
                note.id(),
                note.meetingOccurrenceId(),
                note.body(),
                note.createdByUserId(),
                note.updatedByUserId(),
                note.updatedAt(),
                note.version()
        );
    }

    private PrivateNoteResponse toPrivate(PrivateNote note) {
        return new PrivateNoteResponse(
                note.id(),
                note.meetingOccurrenceId(),
                note.ownerUserId(),
                note.body(),
                note.aiUseAllowed(),
                note.updatedAt(),
                note.version()
        );
    }

    private AgendaResponse toAgenda(MeetingAgenda agenda) {
        return new AgendaResponse(
                agenda.id(),
                agenda.meetingOccurrenceId(),
                agenda.items(),
                agenda.updatedByUserId(),
                agenda.updatedAt(),
                agenda.version()
        );
    }

    private OpenTaskResponse toTask(OpenTask task) {
        return new OpenTaskResponse(
                task.id(),
                task.meetingOccurrenceId(),
                task.title(),
                task.assigneeUserId(),
                task.open(),
                task.sourceMeetingOccurrenceId(),
                task.createdAt()
        );
    }
}
