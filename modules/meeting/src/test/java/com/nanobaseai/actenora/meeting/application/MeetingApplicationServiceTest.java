package com.nanobaseai.actenora.meeting.application;

import com.nanobaseai.actenora.meeting.api.dto.CreateBusinessContextRequest;
import com.nanobaseai.actenora.meeting.api.dto.CreateMeetingRequest;
import com.nanobaseai.actenora.meeting.api.dto.CursorPageRequest;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.meeting.api.dto.MeetingStatusTransitionRequest;
import com.nanobaseai.actenora.meeting.api.dto.ParticipantResponse;
import com.nanobaseai.actenora.meeting.api.dto.UpdateMeetingRequest;
import com.nanobaseai.actenora.meeting.api.event.MeetingIntegrationEvents;
import com.nanobaseai.actenora.meeting.domain.exception.BusinessContextNotFoundException;
import com.nanobaseai.actenora.meeting.domain.exception.DuplicateBusinessContextException;
import com.nanobaseai.actenora.meeting.domain.exception.DuplicateGraphIdentityException;
import com.nanobaseai.actenora.meeting.domain.exception.DuplicateOccurrenceIdentityException;
import com.nanobaseai.actenora.meeting.domain.exception.InvalidDateRangeException;
import com.nanobaseai.actenora.meeting.domain.exception.InvalidMeetingTransitionException;
import com.nanobaseai.actenora.meeting.domain.exception.InvalidParticipantException;
import com.nanobaseai.actenora.meeting.domain.collaboration.UnauthorizedMeetingAccessException;
import com.nanobaseai.actenora.meeting.domain.exception.MeetingNotFoundException;
import com.nanobaseai.actenora.meeting.domain.exception.OptimisticLockConflictException;
import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrenceStatus;
import com.nanobaseai.actenora.meeting.domain.model.ProcessingPriority;
import com.nanobaseai.actenora.meeting.infrastructure.audit.InMemoryMeetingAuditPort;
import com.nanobaseai.actenora.meeting.infrastructure.messaging.InMemoryMeetingEventPublisher;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.InMemoryBusinessContextRepository;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.InMemoryMeetingOccurrenceRepository;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.InMemoryMeetingParticipantRepository;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.InMemoryMeetingSeriesRepository;
import com.nanobaseai.actenora.meeting.infrastructure.quota.NoOpMeetingQuotaPort;
import com.nanobaseai.actenora.meeting.infrastructure.tenancy.FixedTenantContext;
import com.nanobaseai.actenora.meeting.infrastructure.time.SystemClockPort;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeetingApplicationServiceTest {

    private final TenantId tenantA = TenantId.random();
    private final TenantId tenantB = TenantId.random();
    private final UUID actorA = UUID.randomUUID();
    private final UUID actorB = UUID.randomUUID();

    private FixedTenantContext tenantContext;
    private InMemoryBusinessContextRepository businessContexts;
    private InMemoryMeetingSeriesRepository series;
    private InMemoryMeetingOccurrenceRepository occurrences;
    private InMemoryMeetingParticipantRepository participants;
    private InMemoryMeetingEventPublisher events;
    private InMemoryMeetingAuditPort audit;
    private MeetingApiFacade api;
    private BusinessContextApplicationService businessContextsService;

    @BeforeEach
    void setUp() {
        tenantContext = new FixedTenantContext(tenantA, actorA);
        businessContexts = new InMemoryBusinessContextRepository();
        series = new InMemoryMeetingSeriesRepository();
        occurrences = new InMemoryMeetingOccurrenceRepository();
        participants = new InMemoryMeetingParticipantRepository();
        events = new InMemoryMeetingEventPublisher();
        audit = new InMemoryMeetingAuditPort();
        ClockPortAdapter clock = new ClockPortAdapter();
        MeetingApplicationService meetingService = new MeetingApplicationService(
                tenantContext, businessContexts, series, occurrences, participants, events, audit,
                new NoOpMeetingQuotaPort(), clock
        );
        businessContextsService = new BusinessContextApplicationService(
                tenantContext, businessContexts, audit, clock
        );
        api = new MeetingApiFacade(meetingService, businessContextsService);
    }

    @Test
    void createMeetingEmitsMeetingCreatedAndAudit() {
        UUID contextId = createContext().id();
        MeetingResponse meeting = createMeeting(contextId, "graph-1", "ical-1", Instant.parse("2026-07-25T10:00:00Z"));

        assertEquals(MeetingOccurrenceStatus.DRAFT, meeting.status());
        assertEquals(1, events.published().size());
        assertInstanceOf(MeetingIntegrationEvents.MeetingCreated.class, events.published().getFirst());
        assertTrue(audit.entries().stream().anyMatch(e -> e.action().equals("MEETING_CREATED")));
    }

    @Test
    void statusTransitionMatrixHappyPathEmitsLifecycleEvents() {
        MeetingResponse meeting = createMeeting(createContext().id(), "g-life", "i-life", Instant.parse("2026-07-25T10:00:00Z"));

        MeetingResponse scheduled = api.transitionMeetingStatus(meeting.id(),
                new MeetingStatusTransitionRequest(MeetingOccurrenceStatus.SCHEDULED, meeting.version()));
        assertEquals(MeetingOccurrenceStatus.SCHEDULED, scheduled.status());
        assertTrue(events.published().stream().anyMatch(MeetingIntegrationEvents.MeetingScheduled.class::isInstance));

        MeetingResponse started = api.transitionMeetingStatus(scheduled.id(),
                new MeetingStatusTransitionRequest(MeetingOccurrenceStatus.IN_PROGRESS, scheduled.version()));
        assertEquals(MeetingOccurrenceStatus.IN_PROGRESS, started.status());
        assertTrue(events.published().stream().anyMatch(MeetingIntegrationEvents.MeetingStarted.class::isInstance));

        MeetingResponse ended = api.transitionMeetingStatus(started.id(),
                new MeetingStatusTransitionRequest(MeetingOccurrenceStatus.ENDED, started.version()));
        assertEquals(MeetingOccurrenceStatus.ENDED, ended.status());
        assertTrue(events.published().stream().anyMatch(MeetingIntegrationEvents.MeetingEnded.class::isInstance));
    }

    @Test
    void invalidTransitionThrows() {
        MeetingResponse meeting = createMeeting(createContext().id(), "g-bad", "i-bad", Instant.parse("2026-07-25T10:00:00Z"));
        assertThrows(InvalidMeetingTransitionException.class, () ->
                api.transitionMeetingStatus(meeting.id(),
                        new MeetingStatusTransitionRequest(MeetingOccurrenceStatus.ENDED, meeting.version())));
    }

    @Test
    void duplicateGraphIdentityRejected() {
        UUID contextId = createContext().id();
        createMeeting(contextId, "same-graph", "ical-a", Instant.parse("2026-07-25T10:00:00Z"));
        assertThrows(DuplicateGraphIdentityException.class, () ->
                createMeeting(contextId, "same-graph", "ical-b", Instant.parse("2026-07-25T11:00:00Z")));
    }

    @Test
    void duplicateOccurrenceIdentityRejected() {
        UUID contextId = createContext().id();
        Instant original = Instant.parse("2026-07-25T10:00:00Z");
        createMeeting(contextId, "graph-x", "same-ical", original);
        assertThrows(DuplicateOccurrenceIdentityException.class, () ->
                createMeeting(contextId, "graph-y", "same-ical", original));
    }

    @Test
    void tenantIsolationPreventsCrossTenantRead() {
        UUID contextId = createContext().id();
        MeetingResponse meeting = createMeeting(contextId, "g-iso", "i-iso", Instant.parse("2026-07-25T10:00:00Z"));

        tenantContext.use(tenantB, actorB);
        assertThrows(UnauthorizedMeetingAccessException.class, () -> api.getMeeting(meeting.id()));
    }

    @Test
    void optimisticLockingRejectsStaleVersion() {
        MeetingResponse meeting = createMeeting(createContext().id(), "g-ol", "i-ol", Instant.parse("2026-07-25T10:00:00Z"));
        api.updateMeeting(meeting.id(), new UpdateMeetingRequest(
                "Renamed", null, null, null, null, null, null, null, null, null, meeting.version()
        ));
        assertThrows(OptimisticLockConflictException.class, () ->
                api.updateMeeting(meeting.id(), new UpdateMeetingRequest(
                        "Stale", null, null, null, null, null, null, null, null, null, meeting.version()
                )));
    }

    @Test
    void invalidDateRangeRejected() {
        UUID contextId = createContext().id();
        Instant start = Instant.parse("2026-07-25T12:00:00Z");
        Instant end = Instant.parse("2026-07-25T11:00:00Z");
        assertThrows(InvalidDateRangeException.class, () ->
                api.createMeeting(new CreateMeetingRequest(
                        contextId, null, null, "g-date", "i-date", start, null, null, null,
                        "Bad range", null, start, end, null, List.of()
                )));
    }

    @Test
    void externalParticipantRequiresEmailAndAllowsMissingEntraId() {
        UUID contextId = createContext().id();
        Instant start = Instant.parse("2026-07-25T10:00:00Z");
        MeetingResponse meeting = api.createMeeting(new CreateMeetingRequest(
                contextId, null, null, "g-ext", "i-ext", start, null, null, null,
                "External guests", null, start, start.plusSeconds(3600), null,
                List.of(new CreateMeetingRequest.ParticipantInput(
                        null, "Guest", "guest@example.com", "OPTIONAL", true
                ))
        ));
        List<ParticipantResponse> listed = api.listParticipants(meeting.id());
        assertEquals(1, listed.size());
        assertTrue(listed.getFirst().external());
        assertEquals("guest@example.com", listed.getFirst().email());
    }

    @Test
    void externalParticipantWithoutEmailFails() {
        UUID contextId = createContext().id();
        Instant start = Instant.parse("2026-07-25T10:00:00Z");
        assertThrows(InvalidParticipantException.class, () ->
                api.createMeeting(new CreateMeetingRequest(
                        contextId, null, null, "g-ext2", "i-ext2", start, null, null, null,
                        "Bad guest", null, start, start.plusSeconds(3600), null,
                        List.of(new CreateMeetingRequest.ParticipantInput(
                                null, "Guest", " ", "OPTIONAL", true
                        ))
                )));
    }

    @Test
    void priorityChangeEmitsEventAndAudit() {
        MeetingResponse meeting = createMeeting(createContext().id(), "g-prio", "i-prio", Instant.parse("2026-07-25T10:00:00Z"));
        MeetingResponse updated = api.updateMeeting(meeting.id(), new UpdateMeetingRequest(
                null, null, null, null, null, null, null, null, null,
                ProcessingPriority.CRITICAL, meeting.version()
        ));
        assertEquals(ProcessingPriority.CRITICAL, updated.processingPriority());
        assertTrue(events.published().stream().anyMatch(MeetingIntegrationEvents.MeetingPriorityChanged.class::isInstance));
        assertTrue(audit.entries().stream().anyMatch(e -> e.action().equals("MEETING_UPDATED")));
    }

    @Test
    void listFiltersByStatusAndPaginates() {
        UUID contextId = createContext().id();
        createMeeting(contextId, "g1", "i1", Instant.parse("2026-07-25T10:00:00Z"));
        MeetingResponse second = createMeeting(contextId, "g2", "i2", Instant.parse("2026-07-25T11:00:00Z"));
        api.transitionMeetingStatus(second.id(),
                new MeetingStatusTransitionRequest(MeetingOccurrenceStatus.SCHEDULED, second.version()));

        var page = api.listMeetings(new CursorPageRequest(MeetingOccurrenceStatus.SCHEDULED, contextId, null, 10));
        assertEquals(1, page.items().size());
        assertEquals(second.id(), page.items().getFirst().id());
    }

    @Test
    void cancelledTransitionEmitsMeetingCancelled() {
        MeetingResponse meeting = createMeeting(createContext().id(), "g-can", "i-can", Instant.parse("2026-07-25T10:00:00Z"));
        api.transitionMeetingStatus(meeting.id(),
                new MeetingStatusTransitionRequest(MeetingOccurrenceStatus.CANCELLED, meeting.version()));
        assertTrue(events.published().stream().anyMatch(MeetingIntegrationEvents.MeetingCancelled.class::isInstance));
    }

    @Test
    void duplicateBusinessContextReferenceCodeRejected() {
        businessContextsService.create(new CreateBusinessContextRequest(
                "PROJECT", "PRJ-DUP", "First", "desc"
        ));
        assertThrows(DuplicateBusinessContextException.class, () ->
                businessContextsService.create(new CreateBusinessContextRequest(
                        "PROJECT", "prj-dup", "Second", "desc"
                )));
    }

    @Test
    void businessContextTenantIsolationPreventsCrossTenantRead() {
        var context = createContext();
        tenantContext.use(tenantB, actorB);
        assertThrows(BusinessContextNotFoundException.class, () -> businessContextsService.get(context.id()));
    }

    private com.nanobaseai.actenora.meeting.api.dto.BusinessContextResponse createContext() {
        return businessContextsService.create(new CreateBusinessContextRequest(
                "PROJECT", "PRJ-" + UUID.randomUUID().toString().substring(0, 8), "Project", "desc"
        ));
    }

    private MeetingResponse createMeeting(UUID contextId, String graphId, String ical, Instant start) {
        return api.createMeeting(new CreateMeetingRequest(
                contextId, null, null, graphId, ical, start, null, null, null,
                "Standup", null, start, start.plusSeconds(1800), ProcessingPriority.NORMAL, List.of()
        ));
    }

    private static final class ClockPortAdapter implements com.nanobaseai.actenora.meeting.application.port.ClockPort {
        private final SystemClockPort delegate = new SystemClockPort();

        @Override
        public Instant now() {
            return delegate.now();
        }
    }
}
