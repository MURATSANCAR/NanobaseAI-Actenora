package com.nanobaseai.actenora.meeting.faz28;

import com.nanobaseai.actenora.meeting.api.dto.CreateBusinessContextRequest;
import com.nanobaseai.actenora.meeting.api.dto.CreateMeetingRequest;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.meeting.application.BusinessContextApplicationService;
import com.nanobaseai.actenora.meeting.application.MeetingApiFacade;
import com.nanobaseai.actenora.meeting.application.MeetingApplicationService;
import com.nanobaseai.actenora.meeting.domain.exception.DuplicateGraphIdentityException;
import com.nanobaseai.actenora.meeting.domain.model.ProcessingPriority;
import com.nanobaseai.actenora.meeting.infrastructure.audit.InMemoryMeetingAuditPort;
import com.nanobaseai.actenora.meeting.infrastructure.messaging.InMemoryMeetingEventPublisher;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.InMemoryBusinessContextRepository;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.InMemoryMeetingOccurrenceRepository;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.InMemoryMeetingParticipantRepository;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.InMemoryMeetingSeriesRepository;
import com.nanobaseai.actenora.meeting.infrastructure.tenancy.FixedTenantContext;
import com.nanobaseai.actenora.meeting.infrastructure.time.SystemClockPort;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FAZ 28 load scenarios: daily 30 meetings, ~40 min average, 100-meeting hour burst.
 */
class DailyMeetingLoadScenarioTest {

    private static final int DAILY_TARGET = 30;
    private static final int BURST_TARGET = 100;
    private static final Duration AVG_DURATION = Duration.ofMinutes(40);

    private FixedTenantContext tenantContext;
    private InMemoryMeetingOccurrenceRepository occurrences;
    private InMemoryMeetingEventPublisher events;
    private MeetingApiFacade api;
    private BusinessContextApplicationService businessContextsService;
    private UUID contextId;

    @BeforeEach
    void setUp() {
        TenantId tenant = TenantId.random();
        tenantContext = new FixedTenantContext(tenant, UUID.randomUUID());
        InMemoryBusinessContextRepository businessContexts = new InMemoryBusinessContextRepository();
        InMemoryMeetingSeriesRepository series = new InMemoryMeetingSeriesRepository();
        occurrences = new InMemoryMeetingOccurrenceRepository();
        InMemoryMeetingParticipantRepository participants = new InMemoryMeetingParticipantRepository();
        events = new InMemoryMeetingEventPublisher();
        InMemoryMeetingAuditPort audit = new InMemoryMeetingAuditPort();
        ClockPortAdapter clock = new ClockPortAdapter();
        MeetingApplicationService meetingService = new MeetingApplicationService(
                tenantContext, businessContexts, series, occurrences, participants, events, audit, clock
        );
        businessContextsService = new BusinessContextApplicationService(
                tenantContext, businessContexts, audit, clock
        );
        api = new MeetingApiFacade(meetingService, businessContextsService);
        contextId = businessContextsService.create(new CreateBusinessContextRequest(
                "PROJECT", "PRJ-LOAD", "Load Project", "FAZ28"
        )).id();
    }

    @Test
    void dailyThirtyMeetings_persistWithoutDuplicatesOrLoss() {
        Instant dayStart = Instant.parse("2026-07-25T08:00:00Z");
        Set<UUID> ids = new HashSet<>();
        List<Duration> durations = new ArrayList<>();

        for (int i = 0; i < DAILY_TARGET; i++) {
            Instant start = dayStart.plus(Duration.ofMinutes(i * 20L));
            Instant end = start.plus(AVG_DURATION);
            MeetingResponse meeting = create(i, start, end, ProcessingPriority.NORMAL);
            ids.add(meeting.id());
            durations.add(Duration.between(meeting.scheduledStartAt(), meeting.scheduledEndAt()));
        }

        assertEquals(DAILY_TARGET, ids.size());
        assertEquals(DAILY_TARGET, countMeetings());
        double avgMinutes = durations.stream().mapToLong(Duration::toMinutes).average().orElse(0);
        assertEquals(40.0, avgMinutes, 0.01);
        assertTrue(events.published().size() >= DAILY_TARGET);
    }

    @Test
    void hundredMeetingsEndingSameHour_burstDoesNotLoseOrDuplicateRecords() {
        Instant hourEnd = Instant.parse("2026-07-25T17:00:00Z");
        Set<String> graphIds = new HashSet<>();
        Set<UUID> meetingIds = new HashSet<>();

        for (int i = 0; i < BURST_TARGET; i++) {
            Instant end = hourEnd.minusSeconds(i);
            Instant start = end.minus(AVG_DURATION);
            MeetingResponse meeting = create(
                    i,
                    start,
                    end,
                    i % 10 == 0 ? ProcessingPriority.CRITICAL : ProcessingPriority.NORMAL
            );
            meetingIds.add(meeting.id());
            graphIds.add(meeting.graphEventImmutableId());
            assertTrue(meeting.scheduledEndAt().compareTo(hourEnd.minus(Duration.ofHours(1))) > 0
                    || meeting.scheduledEndAt().equals(hourEnd)
                    || !meeting.scheduledEndAt().isAfter(hourEnd));
        }

        assertEquals(BURST_TARGET, meetingIds.size());
        assertEquals(BURST_TARGET, graphIds.size());
        assertEquals(BURST_TARGET, countMeetings());

        assertThrows(DuplicateGraphIdentityException.class, () ->
                create(0, hourEnd.minus(AVG_DURATION), hourEnd, ProcessingPriority.NORMAL));
    }

    private long countMeetings() {
        return occurrences.findByTenant(
                tenantContext.requireTenantId(), null, null, null, 1_000
        ).items().size();
    }

    private MeetingResponse create(int index, Instant start, Instant end, ProcessingPriority priority) {
        return api.createMeeting(new CreateMeetingRequest(
                contextId,
                null,
                null,
                "graph-load-" + index,
                "ical-load-" + index,
                start,
                null,
                null,
                null,
                "Meeting " + index,
                null,
                start,
                end,
                priority,
                List.of()
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
