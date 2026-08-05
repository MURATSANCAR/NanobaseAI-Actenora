package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.api.dto.ApplyAttendanceRequest;
import com.nanobaseai.actenora.meeting.api.dto.BusinessContextResponse;
import com.nanobaseai.actenora.meeting.api.dto.CreateBusinessContextRequest;
import com.nanobaseai.actenora.meeting.api.dto.CreateMeetingRequest;
import com.nanobaseai.actenora.meeting.api.dto.CursorPageRequest;
import com.nanobaseai.actenora.meeting.api.dto.MeetingListResponse;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.meeting.api.dto.MeetingStatusTransitionRequest;
import com.nanobaseai.actenora.meeting.api.dto.ParticipantResponse;
import com.nanobaseai.actenora.meeting.api.dto.SyncInviteesRequest;
import com.nanobaseai.actenora.meeting.api.dto.UpdateBusinessContextRequest;
import com.nanobaseai.actenora.meeting.api.dto.UpdateMeetingRequest;
import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrenceStatus;
import com.nanobaseai.actenora.meeting.domain.model.ProcessingPriority;
import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.microsoftconnection.application.CalendarSyncService;
import com.nanobaseai.actenora.microsoftconnection.application.MeetingTranscriptService;
import com.nanobaseai.actenora.microsoftconnection.application.OnlineMeetingTranscriptionEnabler;
import com.nanobaseai.actenora.microsoftconnection.application.PollingFallbackService;
import com.nanobaseai.actenora.microsoftconnection.application.ReconciliationJob;
import com.nanobaseai.actenora.microsoftconnection.application.SubscriptionLifecycleService;
import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarDeltaPage;
import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarEvent;
import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarSyncCursor;
import com.nanobaseai.actenora.microsoftconnection.application.model.DirectoryUser;
import com.nanobaseai.actenora.microsoftconnection.application.model.OnlineMeetingMetadata;
import com.nanobaseai.actenora.microsoftconnection.application.model.ParticipantMetadata;
import com.nanobaseai.actenora.microsoftconnection.application.model.TranscriptAvailability;
import com.nanobaseai.actenora.microsoftconnection.application.model.TranscriptContent;
import com.nanobaseai.actenora.microsoftconnection.application.port.CalendarGateway;
import com.nanobaseai.actenora.microsoftconnection.application.port.DirectoryGateway;
import com.nanobaseai.actenora.microsoftconnection.application.port.OnlineMeetingGateway;
import com.nanobaseai.actenora.microsoftconnection.application.port.SubscriptionGateway;
import com.nanobaseai.actenora.microsoftconnection.application.port.TranscriptGateway;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.notification.InMemoryNotificationInbox;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence.InMemoryCalendarSyncCursorStore;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence.InMemorySubscriptionStore;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeetingAttendanceSyncServiceTest {

    @Test
    void emptyReportDoesNotCallApplyAttendance() {
        AtomicReference<ApplyAttendanceRequest> captured = new AtomicReference<>();
        MeetingAttendanceSyncService service = new MeetingAttendanceSyncService(
                microsoftApi(List.of(), Optional.empty()),
                recordingMeetingApi(captured)
        );
        int count = service.syncAttendance(
                TenantId.random(),
                sampleMeeting(),
                "11111111-1111-1111-1111-111111111111",
                "teams-meeting-1"
        );
        assertEquals(0, count);
        assertNull(captured.get());
    }

    @Test
    void resolvesDirectoryEmailAndMarksMissingWhenReliableIdentityPresent() {
        AtomicReference<ApplyAttendanceRequest> captured = new AtomicReference<>();
        String oid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        Instant joined = Instant.parse("2026-08-05T10:00:00Z");
        List<ParticipantMetadata> report = List.of(new ParticipantMetadata(
                oid,
                "Alice",
                null,
                "attendee",
                null,
                joined,
                joined.plusSeconds(120),
                120
        ));
        MeetingAttendanceSyncService service = new MeetingAttendanceSyncService(
                microsoftApi(report, Optional.of(new DirectoryUser(
                        oid, "Alice", "alice@example.com", "alice@example.com"
                ))),
                recordingMeetingApi(captured)
        );

        int count = service.syncAttendance(
                TenantId.random(),
                sampleMeeting(),
                "11111111-1111-1111-1111-111111111111",
                "teams-meeting-1"
        );

        assertEquals(1, count);
        ApplyAttendanceRequest request = captured.get();
        assertTrue(request.markMissingAsAbsent());
        assertEquals(1, request.attended().size());
        ApplyAttendanceRequest.AttendanceRecord row = request.attended().stream()
                .filter(r -> oid.equals(r.entraUserId()))
                .findFirst()
                .orElseThrow();
        assertEquals("alice@example.com", row.email());
    }

    @Test
    void nameOnlyRowsDoNotTriggerMarkMissingAsAbsent() {
        AtomicReference<ApplyAttendanceRequest> captured = new AtomicReference<>();
        Instant joined = Instant.parse("2026-08-05T10:00:00Z");
        List<ParticipantMetadata> report = List.of(new ParticipantMetadata(
                "guest-display-only",
                "Guest Person",
                null,
                "attendee",
                null,
                joined,
                joined.plusSeconds(30),
                30
        ));
        MeetingAttendanceSyncService service = new MeetingAttendanceSyncService(
                microsoftApi(report, Optional.empty()),
                recordingMeetingApi(captured)
        );

        service.syncAttendance(
                TenantId.random(),
                sampleMeeting(),
                "11111111-1111-1111-1111-111111111111",
                "teams-meeting-1"
        );

        assertFalse(captured.get().markMissingAsAbsent());
    }

    @Test
    void zeroAttendanceSecondsSkipped() {
        AtomicReference<ApplyAttendanceRequest> captured = new AtomicReference<>();
        List<ParticipantMetadata> report = List.of(new ParticipantMetadata(
                "11111111-1111-1111-1111-111111111111",
                "Never Joined",
                "never@example.com",
                "attendee",
                "never@example.com",
                null,
                null,
                0
        ));
        MeetingAttendanceSyncService service = new MeetingAttendanceSyncService(
                microsoftApi(report, Optional.empty()),
                recordingMeetingApi(captured)
        );

        int count = service.syncAttendance(
                TenantId.random(),
                sampleMeeting(),
                "11111111-1111-1111-1111-111111111111",
                "teams-meeting-1"
        );
        assertEquals(0, count);
        assertNull(captured.get());
    }

    private static MicrosoftConnectionApi microsoftApi(
            List<ParticipantMetadata> report,
            Optional<DirectoryUser> directoryUser
    ) {
        OnlineMeetingGateway meetings = new OnlineMeetingGateway() {
            @Override
            public Optional<OnlineMeetingMetadata> getByJoinWebUrl(UUID tenantId, String userId, String joinWebUrl) {
                return Optional.empty();
            }

            @Override
            public Optional<OnlineMeetingMetadata> getByMeetingId(UUID tenantId, String userId, String meetingId) {
                return Optional.empty();
            }

            @Override
            public List<ParticipantMetadata> listParticipants(UUID tenantId, String userId, String meetingId) {
                return report;
            }

            @Override
            public void enableTranscription(UUID tenantId, String userId, String meetingId) {
            }
        };
        CalendarGateway calendars = new CalendarGateway() {
            @Override
            public CalendarDeltaPage syncDelta(UUID tenantId, String userId, CalendarSyncCursor cursor) {
                return new CalendarDeltaPage(List.of(), null, null);
            }

            @Override
            public Optional<CalendarEvent> getEvent(UUID tenantId, String userId, String eventId) {
                return Optional.empty();
            }
        };
        TranscriptGateway transcripts = new TranscriptGateway() {
            @Override
            public TranscriptAvailability checkAvailability(UUID tenantId, String userId, String meetingId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<TranscriptContent> download(
                    UUID tenantId, String userId, String meetingId, String transcriptId) {
                return Optional.empty();
            }
        };
        DirectoryGateway directory = (tenantId, objectId) ->
                directoryUser.filter(u -> u.id().equalsIgnoreCase(objectId));

        CalendarSyncService calendarSync = new CalendarSyncService(
                calendars, new InMemoryCalendarSyncCursorStore(), InstantClock.systemUTC());
        InMemorySubscriptionStore subscriptions = new InMemorySubscriptionStore();
        SubscriptionLifecycleService lifecycle = new SubscriptionLifecycleService(
                unusedSubscriptionGateway(),
                subscriptions,
                new InMemoryNotificationInbox(),
                InstantClock.systemUTC(),
                Duration.ofHours(1),
                Duration.ofHours(24)
        );
        PollingFallbackService polling = new PollingFallbackService(calendarSync);
        return new MicrosoftConnectionApi(
                calendarSync,
                new MeetingTranscriptService(meetings, transcripts),
                lifecycle,
                polling,
                new ReconciliationJob(lifecycle, polling),
                (tenantId, request) -> {
                    throw new UnsupportedOperationException();
                },
                (tenantId, request) -> {
                    throw new UnsupportedOperationException();
                },
                subscriptions,
                new OnlineMeetingTranscriptionEnabler(meetings, InstantClock.systemUTC(), false),
                directory
        );
    }

    private static SubscriptionGateway unusedSubscriptionGateway() {
        return new SubscriptionGateway() {
            @Override
            public com.nanobaseai.actenora.microsoftconnection.application.model.GraphSubscription create(
                    UUID tenantId,
                    com.nanobaseai.actenora.microsoftconnection.application.model.SubscriptionCreateRequest request
            ) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.nanobaseai.actenora.microsoftconnection.application.model.GraphSubscription renew(
                    UUID tenantId, String subscriptionId, Instant newExpiration) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<com.nanobaseai.actenora.microsoftconnection.application.model.GraphSubscription> get(
                    UUID tenantId, String subscriptionId) {
                return Optional.empty();
            }

            @Override
            public void delete(UUID tenantId, String subscriptionId) {
            }
        };
    }

    private static MeetingApi recordingMeetingApi(AtomicReference<ApplyAttendanceRequest> captured) {
        return new MeetingApi() {
            @Override
            public List<ParticipantResponse> applyAttendance(UUID meetingId, ApplyAttendanceRequest request) {
                captured.set(request);
                return List.of();
            }

            @Override
            public MeetingResponse createMeeting(CreateMeetingRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public MeetingResponse updateMeeting(UUID meetingId, UpdateMeetingRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public MeetingResponse getMeeting(UUID meetingId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<MeetingResponse> findByGraphEventImmutableId(String graphEventImmutableId) {
                return Optional.empty();
            }

            @Override
            public MeetingListResponse listMeetings(CursorPageRequest pageRequest) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<MeetingResponse> searchMeetings(String query, MeetingOccurrenceStatus status, int limit) {
                return List.of();
            }

            @Override
            public MeetingResponse transitionMeetingStatus(UUID meetingId, MeetingStatusTransitionRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public MeetingResponse advanceMeetingLifecycle(UUID meetingId, boolean cancelled) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<MeetingResponse> listMeetingsDueForLifecycleAdvance(int limit) {
                return List.of();
            }

            @Override
            public List<ParticipantResponse> listParticipants(UUID meetingId) {
                return List.of();
            }

            @Override
            public List<ParticipantResponse> syncInvitees(UUID meetingId, SyncInviteesRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public BusinessContextResponse createBusinessContext(CreateBusinessContextRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<BusinessContextResponse> listBusinessContexts() {
                return List.of();
            }

            @Override
            public BusinessContextResponse updateBusinessContext(UUID id, UpdateBusinessContextRequest request) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static MeetingResponse sampleMeeting() {
        Instant start = Instant.parse("2026-08-05T09:00:00Z");
        Instant end = start.plusSeconds(3600);
        return new MeetingResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                "g-event",
                "ical",
                start,
                "teams-meeting-1",
                null,
                null,
                "Sync test",
                UUID.randomUUID(),
                start,
                end,
                start,
                end,
                MeetingOccurrenceStatus.ENDED,
                ProcessingPriority.NORMAL,
                start,
                end,
                1L
        );
    }
}
