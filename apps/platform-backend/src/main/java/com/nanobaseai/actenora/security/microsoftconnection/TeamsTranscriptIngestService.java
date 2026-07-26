package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.meeting.api.dto.ParticipantResponse;
import com.nanobaseai.actenora.meeting.api.dto.UpdateMeetingRequest;
import com.nanobaseai.actenora.meeting.domain.model.ParticipantType;
import com.nanobaseai.actenora.meeting.infrastructure.tenancy.FixedTenantContext;
import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.microsoftconnection.application.model.OnlineMeetingMetadata;
import com.nanobaseai.actenora.microsoftconnection.application.model.TranscriptAvailability;
import com.nanobaseai.actenora.microsoftconnection.application.model.TranscriptContent;
import com.nanobaseai.actenora.microsoftconnection.application.port.SubscriptionStore;
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
 * Resolves Graph onlineMeeting id from stored teamsMeetingId or JoinWebUrl.
 */
public final class TeamsTranscriptIngestService {

    private static final Logger log = LoggerFactory.getLogger(TeamsTranscriptIngestService.class);

    private final MicrosoftConnectionApi microsoftConnectionApi;
    private final TranscriptApi transcriptApi;
    private final MeetingApi meetingApi;
    private final FixedTenantContext tenantContext;
    private final String defaultMailboxUserId;
    private final SubscriptionStore subscriptionStore;

    public TeamsTranscriptIngestService(
            MicrosoftConnectionApi microsoftConnectionApi,
            TranscriptApi transcriptApi,
            MeetingApi meetingApi,
            FixedTenantContext tenantContext,
            SubscriptionStore subscriptionStore,
            String defaultMailboxUserId
    ) {
        this.microsoftConnectionApi = Objects.requireNonNull(microsoftConnectionApi);
        this.transcriptApi = Objects.requireNonNull(transcriptApi);
        this.meetingApi = Objects.requireNonNull(meetingApi);
        this.tenantContext = Objects.requireNonNull(tenantContext);
        this.subscriptionStore = Objects.requireNonNull(subscriptionStore);
        this.defaultMailboxUserId = defaultMailboxUserId;
    }

    public PollResult pollMeeting(TenantId tenantId, UUID meetingOccurrenceId) {
        tenantContext.use(tenantId, CalendarMeetingUpsertAdapter.SYSTEM_ACTOR);
        MeetingResponse meeting = meetingApi.getMeeting(meetingOccurrenceId);
        String graphUserId = resolveGraphUserId(tenantId, meeting);
        if (!StringUtils.hasText(graphUserId)) {
            log.warn("No Graph mailbox user for transcript poll meetingId={} tenantId={}",
                    meetingOccurrenceId, tenantId.value());
            return PollResult.CONFIGURATION_MISSING;
        }
        String teamsMeetingId = resolveOnlineMeetingId(tenantId, graphUserId, meeting);
        if (!StringUtils.hasText(teamsMeetingId)) {
            log.warn("Unable to resolve Graph onlineMeetingId meetingId={} tenantId={}",
                    meetingOccurrenceId, tenantId.value());
            return PollResult.NOT_AVAILABLE;
        }
        TranscriptAvailability availability = microsoftConnectionApi.checkTranscript(
                tenantId.value(), graphUserId, teamsMeetingId);
        if (!availability.available() || availability.transcripts().isEmpty()) {
            return PollResult.NOT_AVAILABLE;
        }
        TranscriptAvailability.TranscriptRef ref = availability.firstTranscript().orElseThrow();
        Optional<TranscriptContent> content = microsoftConnectionApi.downloadTranscript(
                tenantId.value(), graphUserId, teamsMeetingId, ref.transcriptId());
        if (content.isEmpty()) {
            return PollResult.NOT_AVAILABLE;
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
        return uploaded.duplicate() ? PollResult.ALREADY_INGESTED : PollResult.INGESTED;
    }

    private String resolveOnlineMeetingId(TenantId tenantId, String graphUserId, MeetingResponse meeting) {
        if (StringUtils.hasText(meeting.teamsMeetingId())) {
            Optional<OnlineMeetingMetadata> byId = microsoftConnectionApi.getMeeting(
                    tenantId.value(), graphUserId, meeting.teamsMeetingId());
            if (byId.isPresent()) {
                return byId.get().meetingId();
            }
            // Stored value may be a conferenceId; fall through to JoinWebUrl resolve.
        }
        if (!StringUtils.hasText(meeting.joinWebUrl())) {
            return meeting.teamsMeetingId();
        }
        Optional<OnlineMeetingMetadata> byJoin = microsoftConnectionApi.getMeetingByJoinWebUrl(
                tenantId.value(), graphUserId, meeting.joinWebUrl());
        if (byJoin.isEmpty()) {
            return null;
        }
        String resolvedId = byJoin.get().meetingId();
        persistResolvedTeamsMeetingId(meeting, resolvedId);
        return resolvedId;
    }

    private void persistResolvedTeamsMeetingId(MeetingResponse meeting, String teamsMeetingId) {
        if (teamsMeetingId.equals(meeting.teamsMeetingId())) {
            return;
        }
        try {
            meetingApi.updateMeeting(meeting.id(), new UpdateMeetingRequest(
                    meeting.title(),
                    meeting.scheduledStartAt(),
                    meeting.scheduledEndAt(),
                    meeting.graphEventImmutableId(),
                    meeting.icalUid(),
                    meeting.originalStartAt(),
                    teamsMeetingId,
                    meeting.chatId(),
                    meeting.joinWebUrl(),
                    meeting.processingPriority(),
                    meeting.version()
            ));
        } catch (RuntimeException ex) {
            log.debug("Could not persist resolved teamsMeetingId for {}: {}", meeting.id(), ex.getMessage());
        }
    }

    private String resolveGraphUserId(TenantId tenantId, MeetingResponse meeting) {
        List<ParticipantResponse> participants = meetingApi.listParticipants(meeting.id());
        Optional<ParticipantResponse> organizer = participants.stream()
                .filter(p -> p.participantType() == ParticipantType.ORGANIZER)
                .findFirst();
        if (organizer.isPresent()) {
            ParticipantResponse p = organizer.get();
            if (StringUtils.hasText(p.entraUserId())) {
                return p.entraUserId();
            }
            if (StringUtils.hasText(p.email())) {
                return p.email();
            }
        }
        List<String> subscriptionMailboxes = subscriptionStore.findAllForTenant(tenantId.value()).stream()
                .map(subscription -> GraphChangeNotificationProcessor.parseMailboxUserId(subscription.resource()))
                .flatMap(Optional::stream)
                .distinct()
                .toList();
        if (subscriptionMailboxes.size() == 1) {
            return subscriptionMailboxes.getFirst();
        }
        if (StringUtils.hasText(defaultMailboxUserId)) {
            return defaultMailboxUserId;
        }
        return null;
    }

    public enum PollResult {
        INGESTED,
        ALREADY_INGESTED,
        NOT_AVAILABLE,
        CONFIGURATION_MISSING
    }
}
