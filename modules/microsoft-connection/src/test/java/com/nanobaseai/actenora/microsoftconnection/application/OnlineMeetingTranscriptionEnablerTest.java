package com.nanobaseai.actenora.microsoftconnection.application;

import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarEvent;
import com.nanobaseai.actenora.microsoftconnection.application.model.OnlineMeetingMetadata;
import com.nanobaseai.actenora.microsoftconnection.application.model.ParticipantMetadata;
import com.nanobaseai.actenora.microsoftconnection.application.port.OnlineMeetingGateway;
import com.nanobaseai.actenora.microsoftconnection.domain.identity.ImmutableGraphEventIdentity;
import com.nanobaseai.actenora.microsoftconnection.domain.identity.SeriesOccurrenceKind;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OnlineMeetingTranscriptionEnablerTest {

    @Test
    void enablesTranscriptionForUpcomingTeamsMeetings() {
        RecordingGateway gateway = new RecordingGateway();
        var enabler = new OnlineMeetingTranscriptionEnabler(
                gateway,
                new InstantClock(Clock.fixed(Instant.parse("2026-07-26T10:00:00Z"), Clock.systemUTC().getZone())),
                true
        );
        UUID tenantId = UUID.randomUUID();

        enabler.enableForUpcomingMeetings(tenantId, "organizer@example.com", List.of(
                teamsEvent("evt-1", "https://teams.example/join/1", null),
                teamsEvent("evt-2", null, "meeting-id-2"),
                teamsEvent("evt-past", "https://teams.example/join/past", null, Instant.parse("2026-07-26T08:00:00Z"), Instant.parse("2026-07-26T09:00:00Z"))
        ));

        assertEquals(2, gateway.enabledCount.get());
    }

    private static CalendarEvent teamsEvent(
            String graphId,
            String joinUrl,
            String onlineMeetingId
    ) {
        return teamsEvent(
                graphId,
                joinUrl,
                onlineMeetingId,
                Instant.parse("2026-07-26T11:00:00Z"),
                Instant.parse("2026-07-26T12:00:00Z"));
    }

    private static CalendarEvent teamsEvent(
            String graphId,
            String joinUrl,
            String onlineMeetingId,
            Instant start,
            Instant end
    ) {
        return new CalendarEvent(
                new ImmutableGraphEventIdentity(graphId),
                graphId,
                null,
                "ical-" + graphId,
                SeriesOccurrenceKind.SINGLE,
                "Standup",
                start,
                end,
                null,
                joinUrl,
                onlineMeetingId,
                false,
                List.of(new ParticipantMetadata("a@example.com", "A", "a@example.com", "required", "a@example.com"))
        );
    }

    private static final class RecordingGateway implements OnlineMeetingGateway {
        private final AtomicInteger enabledCount = new AtomicInteger();

        @Override
        public Optional<OnlineMeetingMetadata> getByJoinWebUrl(UUID tenantId, String userId, String joinWebUrl) {
            return Optional.of(new OnlineMeetingMetadata(
                    "resolved-" + joinWebUrl,
                    joinWebUrl,
                    "subject",
                    null,
                    null,
                    null,
                    false
            ));
        }

        @Override
        public Optional<OnlineMeetingMetadata> getByMeetingId(UUID tenantId, String userId, String meetingId) {
            return Optional.empty();
        }

        @Override
        public List<ParticipantMetadata> listParticipants(UUID tenantId, String userId, String meetingId) {
            return List.of();
        }

        @Override
        public void enableTranscription(UUID tenantId, String userId, String meetingId) {
            enabledCount.incrementAndGet();
        }
    }
}
