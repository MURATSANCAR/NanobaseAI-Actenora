package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.meeting.api.dto.ParticipantResponse;
import com.nanobaseai.actenora.meeting.domain.model.ParticipantType;
import com.nanobaseai.actenora.meeting.infrastructure.tenancy.FixedTenantContext;
import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.microsoftconnection.application.model.TranscriptAvailability;
import com.nanobaseai.actenora.microsoftconnection.application.model.TranscriptContent;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.api.TranscriptApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Polls Teams Graph for meeting transcripts and ingests VTT via {@link TranscriptApi}.
 */
public final class TeamsTranscriptIngestService {

    private static final Logger log = LoggerFactory.getLogger(TeamsTranscriptIngestService.class);

    private final MicrosoftConnectionApi microsoftConnectionApi;
    private final TranscriptApi transcriptApi;
    private final MeetingApi meetingApi;
    private final FixedTenantContext tenantContext;
    private final String defaultMailboxUserId;

    public TeamsTranscriptIngestService(
            MicrosoftConnectionApi microsoftConnectionApi,
            TranscriptApi transcriptApi,
            MeetingApi meetingApi,
            FixedTenantContext tenantContext,
            String defaultMailboxUserId
    ) {
        this.microsoftConnectionApi = Objects.requireNonNull(microsoftConnectionApi);
        this.transcriptApi = Objects.requireNonNull(transcriptApi);
        this.meetingApi = Objects.requireNonNull(meetingApi);
        this.tenantContext = Objects.requireNonNull(tenantContext);
        this.defaultMailboxUserId = defaultMailboxUserId;
    }

    public boolean pollMeeting(TenantId tenantId, UUID meetingOccurrenceId) {
        tenantContext.use(tenantId, CalendarMeetingUpsertAdapter.SYSTEM_ACTOR);
        MeetingResponse meeting = meetingApi.getMeeting(meetingOccurrenceId);
        String teamsMeetingId = resolveTeamsMeetingId(meeting);
        if (!StringUtils.hasText(teamsMeetingId)) {
            return false;
        }
        String graphUserId = resolveGraphUserId(meeting);
        if (!StringUtils.hasText(graphUserId)) {
            log.warn("No Graph mailbox user for transcript poll meetingId={} tenantId={}",
                    meetingOccurrenceId, tenantId.value());
            return false;
        }
        TranscriptAvailability availability = microsoftConnectionApi.checkTranscript(
                tenantId.value(), graphUserId, teamsMeetingId);
        if (!availability.available() || availability.transcripts().isEmpty()) {
            return false;
        }
        TranscriptAvailability.TranscriptRef ref = availability.firstTranscript().orElseThrow();
        Optional<TranscriptContent> content = microsoftConnectionApi.downloadTranscript(
                tenantId.value(), graphUserId, teamsMeetingId, ref.transcriptId());
        if (content.isEmpty()) {
            return false;
        }
        var uploaded = transcriptApi.ingestFromGraphVtt(
                tenantId,
                meetingOccurrenceId,
                ref.transcriptId(),
                content.get().body(),
                null);
        log.info(
                "Teams transcript ingested meetingId={} transcriptId={} duplicate={}",
                meetingOccurrenceId,
                uploaded.transcriptId(),
                uploaded.duplicate());
        return !uploaded.duplicate();
    }

    private String resolveTeamsMeetingId(MeetingResponse meeting) {
        return meeting.teamsMeetingId();
    }

    private String resolveGraphUserId(MeetingResponse meeting) {
        if (StringUtils.hasText(defaultMailboxUserId)) {
            return defaultMailboxUserId;
        }
        List<ParticipantResponse> participants = meetingApi.listParticipants(meeting.id());
        Optional<ParticipantResponse> organizer = participants.stream()
                .filter(p -> p.participantType() == ParticipantType.ORGANIZER)
                .findFirst();
        if (organizer.isPresent()) {
            ParticipantResponse p = organizer.get();
            if (StringUtils.hasText(p.email())) {
                return p.email();
            }
            if (StringUtils.hasText(p.entraUserId())) {
                return p.entraUserId();
            }
        }
        return participants.stream()
                .map(ParticipantResponse::email)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }
}
