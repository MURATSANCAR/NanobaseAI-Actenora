package com.nanobaseai.actenora.meeting.application.collaboration;

import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.CreateMarkerRequest;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.CreateOpenTaskRequest;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.MarkerResponse;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.AgendaResponse;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.PrivateNoteResponse;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.UpdateAgendaRequest;
import com.nanobaseai.actenora.meeting.api.collaboration.CollaborationDtos.UpsertPrivateNoteRequest;
import com.nanobaseai.actenora.meeting.api.dto.CreateBusinessContextRequest;
import com.nanobaseai.actenora.meeting.api.dto.CreateMeetingRequest;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.meeting.application.BusinessContextApplicationService;
import com.nanobaseai.actenora.meeting.application.MeetingApiFacade;
import com.nanobaseai.actenora.meeting.application.MeetingApplicationService;
import com.nanobaseai.actenora.meeting.application.port.ClockPort;
import com.nanobaseai.actenora.meeting.domain.collaboration.InvalidMeetingAppTokenException;
import com.nanobaseai.actenora.meeting.domain.collaboration.MarkerOffsetCalculator;
import com.nanobaseai.actenora.meeting.domain.collaboration.MarkerType;
import com.nanobaseai.actenora.meeting.domain.collaboration.PrivateNoteAccessDeniedException;
import com.nanobaseai.actenora.meeting.domain.collaboration.PrivateNoteAiAccessDeniedException;
import com.nanobaseai.actenora.meeting.domain.collaboration.UnauthorizedMeetingAccessException;
import com.nanobaseai.actenora.meeting.domain.model.ProcessingPriority;
import com.nanobaseai.actenora.meeting.infrastructure.audit.InMemoryMeetingAuditPort;
import com.nanobaseai.actenora.meeting.infrastructure.collaboration.HmacMeetingAppTokenValidator;
import com.nanobaseai.actenora.meeting.infrastructure.collaboration.InMemoryCollaborationIdempotencyStore;
import com.nanobaseai.actenora.meeting.infrastructure.collaboration.InMemoryMeetingAgendaRepository;
import com.nanobaseai.actenora.meeting.infrastructure.collaboration.InMemoryMeetingMarkerRepository;
import com.nanobaseai.actenora.meeting.infrastructure.collaboration.InMemoryOpenTaskRepository;
import com.nanobaseai.actenora.meeting.infrastructure.collaboration.InMemoryPrivateNoteRepository;
import com.nanobaseai.actenora.meeting.infrastructure.collaboration.InMemorySharedNoteRepository;
import com.nanobaseai.actenora.meeting.infrastructure.messaging.InMemoryMeetingEventPublisher;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.InMemoryBusinessContextRepository;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.InMemoryMeetingOccurrenceRepository;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.InMemoryMeetingParticipantRepository;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.InMemoryMeetingSeriesRepository;
import com.nanobaseai.actenora.meeting.infrastructure.quota.NoOpMeetingQuotaPort;
import com.nanobaseai.actenora.meeting.infrastructure.tenancy.FixedTenantContext;
import com.nanobaseai.actenora.meeting.application.collaboration.port.MeetingAppTokenValidator.UntrustedTeamsContext;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeetingCollaborationServiceTest {

    private final TenantId tenantA = TenantId.random();
    private final UUID organizer = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    private FixedTenantContext tenantContext;
    private MutableClock clock;
    private MeetingApiFacade meetingApi;
    private MeetingCollaborationService collaboration;
    private HmacMeetingAppTokenValidator tokenValidator;
    private BusinessContextApplicationService businessContexts;

    @BeforeEach
    void setUp() {
        tenantContext = new FixedTenantContext(tenantA, organizer);
        clock = new MutableClock(Instant.parse("2026-07-25T10:05:00Z"));
        InMemoryBusinessContextRepository contexts = new InMemoryBusinessContextRepository();
        InMemoryMeetingSeriesRepository series = new InMemoryMeetingSeriesRepository();
        InMemoryMeetingOccurrenceRepository occurrences = new InMemoryMeetingOccurrenceRepository();
        InMemoryMeetingParticipantRepository participants = new InMemoryMeetingParticipantRepository();
        InMemoryMeetingEventPublisher events = new InMemoryMeetingEventPublisher();
        InMemoryMeetingAuditPort audit = new InMemoryMeetingAuditPort();

        MeetingApplicationService meetingService = new MeetingApplicationService(
                tenantContext, contexts, series, occurrences, participants, events, audit,
                new NoOpMeetingQuotaPort(), clock
        );
        businessContexts = new BusinessContextApplicationService(tenantContext, contexts, audit, clock);
        meetingApi = new MeetingApiFacade(meetingService, businessContexts);

        MeetingMembershipGuard guard = new MeetingMembershipGuard(occurrences, participants);
        collaboration = new MeetingCollaborationService(
                tenantContext,
                clock,
                guard,
                new InMemoryMeetingMarkerRepository(),
                new InMemorySharedNoteRepository(),
                new InMemoryPrivateNoteRepository(),
                new InMemoryMeetingAgendaRepository(),
                new InMemoryOpenTaskRepository(),
                new InMemoryCollaborationIdempotencyStore()
        );
        tokenValidator = new HmacMeetingAppTokenValidator("test-secret");
    }

    @Test
    void markerCreationUsesServerTimeOffset() {
        MeetingResponse meeting = createMeetingWithMember();
        Instant scheduled = Instant.parse("2026-07-25T10:00:00Z");
        clock.set(Instant.parse("2026-07-25T10:07:30Z"));

        MarkerResponse marker = collaboration.createMarker(
                meeting.id(),
                new CreateMarkerRequest(MarkerType.DECISION, "Ship Friday"),
                "click-1"
        );

        long expected = MarkerOffsetCalculator.offsetMs(scheduled, clock.now());
        assertEquals(expected, marker.offsetMs());
        assertEquals(MarkerType.DECISION, marker.type());
        assertEquals(450_000L, marker.offsetMs());
    }

    @Test
    void duplicateClickIsIdempotent() {
        MeetingResponse meeting = createMeetingWithMember();
        MarkerResponse first = collaboration.createMarker(
                meeting.id(),
                new CreateMarkerRequest(MarkerType.ACTION, "Follow up"),
                "dup-key"
        );
        MarkerResponse second = collaboration.createMarker(
                meeting.id(),
                new CreateMarkerRequest(MarkerType.ACTION, "Follow up again ignored"),
                "dup-key"
        );

        assertEquals(first.id(), second.id());
        assertEquals(first.body(), second.body());
        assertEquals(1, collaboration.listMarkers(meeting.id()).size());
    }

    @Test
    void privateNoteIsolatedFromOrganizerByDefault() {
        MeetingResponse meeting = createMeetingWithMember();

        tenantContext.use(tenantA, member);
        PrivateNoteResponse owned = collaboration.upsertPrivateNote(
                meeting.id(),
                new UpsertPrivateNoteRequest("secret thought", null)
        );

        tenantContext.use(tenantA, organizer);
        assertThrows(PrivateNoteAccessDeniedException.class,
                () -> collaboration.getPrivateNoteById(owned.id()));
        assertEquals(null, collaboration.getOwnPrivateNote(meeting.id()));
    }

    @Test
    void unauthorizedMeetingIsRejected() {
        MeetingResponse meeting = createMeetingWithMember();
        tenantContext.use(tenantA, stranger);
        assertThrows(UnauthorizedMeetingAccessException.class,
                () -> collaboration.createMarker(
                        meeting.id(),
                        new CreateMarkerRequest(MarkerType.RISK, "leak"),
                        "x"
                ));
    }

    @Test
    void offsetCalculationPrefersActualStart() {
        Instant scheduled = Instant.parse("2026-07-25T10:00:00Z");
        Instant actual = Instant.parse("2026-07-25T10:02:00Z");
        Instant now = Instant.parse("2026-07-25T10:05:00Z");
        Instant anchor = MarkerOffsetCalculator.resolveAnchor(actual, scheduled);
        assertEquals(actual, anchor);
        assertEquals(180_000L, MarkerOffsetCalculator.offsetMs(anchor, now));
    }

    @Test
    void sharedAgendaUpdateIsVisibleToMembers() {
        MeetingResponse meeting = createMeetingWithMember();
        AgendaResponse updated = collaboration.updateAgenda(
                meeting.id(),
                new UpdateAgendaRequest(List.of("Kickoff", "Risks", "Next steps"), null),
                "agenda-1"
        );
        assertEquals(List.of("Kickoff", "Risks", "Next steps"), updated.items());

        tenantContext.use(tenantA, member);
        AgendaResponse viewed = collaboration.getAgenda(meeting.id());
        assertEquals(updated.id(), viewed.id());
        assertEquals(updated.items(), viewed.items());
    }

    @Test
    void aiCannotUsePrivateNoteWithoutExplicitConsent() {
        MeetingResponse meeting = createMeetingWithMember();
        tenantContext.use(tenantA, member);
        PrivateNoteResponse note = collaboration.upsertPrivateNote(
                meeting.id(),
                new UpsertPrivateNoteRequest("do not feed to AI", null)
        );

        assertThrows(PrivateNoteAiAccessDeniedException.class,
                () -> collaboration.readPrivateNoteForAi(note.id()));

        collaboration.grantPrivateNoteAiUse(note.id());
        PrivateNoteResponse forAi = collaboration.readPrivateNoteForAi(note.id());
        assertTrue(forAi.aiUseAllowed());
        assertEquals("do not feed to AI", forAi.body());
    }

    @Test
    void teamsContextAloneIsRejectedWithoutBackendToken() {
        assertThrows(InvalidMeetingAppTokenException.class,
                () -> tokenValidator.validate(
                        null,
                        new UntrustedTeamsContext("teams-1", "chat-1", tenantA.value().toString(), organizer.toString())
                ));
    }

    @Test
    void backendTokenValidationBindsTenantAndUser() {
        String token = tokenValidator.issueToken(tenantA, organizer, "teams-1");
        var principal = tokenValidator.validate(
                "Bearer " + token,
                new UntrustedTeamsContext("teams-1", "chat-1", tenantA.value().toString(), organizer.toString())
        );
        assertEquals(tenantA, principal.tenantId());
        assertEquals(organizer, principal.userId());

        assertThrows(InvalidMeetingAppTokenException.class,
                () -> tokenValidator.validate(
                        "Bearer " + token,
                        new UntrustedTeamsContext("teams-1", "chat-1", UUID.randomUUID().toString(), organizer.toString())
                ));
    }

    @Test
    void allMarkerTypesCanBeCreated() {
        MeetingResponse meeting = createMeetingWithMember();
        for (MarkerType type : MarkerType.values()) {
            MarkerResponse marker = collaboration.createMarker(
                    meeting.id(),
                    new CreateMarkerRequest(type, type.name() + " note"),
                    "mk-" + type.name()
            );
            assertEquals(type, marker.type());
        }
        assertEquals(MarkerType.values().length, collaboration.listMarkers(meeting.id()).size());
    }

    @Test
    void openTasksAreListedForMembers() {
        MeetingResponse meeting = createMeetingWithMember();
        collaboration.createOpenTask(
                meeting.id(),
                new CreateOpenTaskRequest("Finish deck", member, null)
        );
        tenantContext.use(tenantA, member);
        assertEquals(1, collaboration.listOpenTasks(meeting.id()).size());
        assertTrue(collaboration.listOpenTasks(meeting.id()).getFirst().open());
    }

    @Test
    void duplicateAgendaClickDoesNotChangeVersionTwice() {
        MeetingResponse meeting = createMeetingWithMember();
        AgendaResponse first = collaboration.updateAgenda(
                meeting.id(),
                new UpdateAgendaRequest(List.of("A"), null),
                "agenda-dup"
        );
        AgendaResponse second = collaboration.updateAgenda(
                meeting.id(),
                new UpdateAgendaRequest(List.of("B"), null),
                "agenda-dup"
        );
        assertEquals(first.version(), second.version());
        assertEquals(first.items(), second.items());
        assertNotEquals(List.of("B"), second.items());
    }

    @Test
    void tenantIsolationBlocksCrossTenantAccess() {
        MeetingResponse meeting = createMeetingWithMember();
        TenantId otherTenant = TenantId.random();
        tenantContext.use(otherTenant, organizer);
        assertThrows(Exception.class,
                () -> collaboration.createMarker(
                        meeting.id(),
                        new CreateMarkerRequest(MarkerType.IMPORTANT, "x"),
                        "iso"
                ));
    }

    private MeetingResponse createMeetingWithMember() {
        tenantContext.use(tenantA, organizer);
        var context = businessContexts.create(new CreateBusinessContextRequest(
                "PROJECT", "PRJ-" + UUID.randomUUID().toString().substring(0, 8), "Project", "desc"
        ));
        Instant start = Instant.parse("2026-07-25T10:00:00Z");
        return meetingApi.createMeeting(new CreateMeetingRequest(
                context.id(), null, null, "graph-" + UUID.randomUUID(), "ical-" + UUID.randomUUID(),
                start, "teams-mtg-1", "chat-1", "https://teams.example/join",
                "Standup", null, start, start.plusSeconds(1800), ProcessingPriority.NORMAL,
                List.of(new CreateMeetingRequest.ParticipantInput(
                        member.toString(), "Member", "member@example.com", "REQUIRED", false
                ))
        ));
    }

    private static final class MutableClock implements ClockPort {
        private final AtomicReference<Instant> now;

        MutableClock(Instant initial) {
            this.now = new AtomicReference<>(initial);
        }

        void set(Instant instant) {
            now.set(instant);
        }

        @Override
        public Instant now() {
            return now.get();
        }
    }
}
