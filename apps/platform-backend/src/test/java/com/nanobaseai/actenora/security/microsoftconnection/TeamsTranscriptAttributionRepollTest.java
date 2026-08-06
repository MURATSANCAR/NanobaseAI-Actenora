package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.meeting.api.dto.ParticipantResponse;
import com.nanobaseai.actenora.meeting.domain.model.AttendanceStatus;
import com.nanobaseai.actenora.meeting.infrastructure.tenancy.FixedTenantContext;
import com.nanobaseai.actenora.microsoftconnection.application.port.SubscriptionStore;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.ExponentialBackoff;
import com.nanobaseai.actenora.transcript.api.TranscriptApi;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamsTranscriptAttributionRepollTest {

    @Test
    void rechecksUnattributedTranscriptOnlyInsideConfiguredWindow() {
        TenantId tenantId = TenantId.random();
        UUID meetingId = UUID.randomUUID();
        Instant endedAt = Instant.parse("2026-08-01T10:00:00Z");
        MeetingResponse meeting = mock(MeetingResponse.class);
        when(meeting.id()).thenReturn(meetingId);
        when(meeting.actualEndAt()).thenReturn(endedAt);
        ParticipantResponse joined = mock(ParticipantResponse.class);
        when(joined.attendanceStatus()).thenReturn(AttendanceStatus.JOINED);
        MeetingApi meetingApi = mock(MeetingApi.class);
        when(meetingApi.listParticipants(meetingId)).thenReturn(List.of(joined));
        TranscriptApi transcripts = mock(TranscriptApi.class);
        when(transcripts.hasTranscriptForMeeting(tenantId, meetingId)).thenReturn(true);
        when(transcripts.hasSpeakerAttributionForMeeting(tenantId, meetingId)).thenReturn(false);

        TeamsTranscriptPollScheduler scheduler = new TeamsTranscriptPollScheduler(
                mock(TeamsTranscriptIngestService.class), meetingApi, mock(FixedTenantContext.class),
                mock(SubscriptionStore.class), transcripts, new InMemoryTranscriptPollWorkStore(),
                new ExponentialBackoff(Duration.ofMinutes(1), Duration.ofHours(1)),
                24, Duration.ofHours(48), Duration.ofMinutes(15), 10, null, Duration.ofDays(30));

        assertTrue(scheduler.needsPoll(meeting, tenantId, endedAt.plus(Duration.ofDays(5))));
        assertFalse(scheduler.needsPoll(meeting, tenantId, endedAt.plus(Duration.ofDays(31))));
    }
}
