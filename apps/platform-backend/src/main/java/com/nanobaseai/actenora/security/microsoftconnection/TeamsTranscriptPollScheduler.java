package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.api.dto.CursorPageRequest;
import com.nanobaseai.actenora.meeting.api.dto.MeetingListResponse;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.meeting.infrastructure.tenancy.FixedTenantContext;
import com.nanobaseai.actenora.microsoftconnection.application.port.SubscriptionStore;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.transcript.api.contract.MeetingOccurrenceContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Event-driven + scheduled Teams transcript polling queue.
 */
public final class TeamsTranscriptPollScheduler {

    private static final Logger log = LoggerFactory.getLogger(TeamsTranscriptPollScheduler.class);

    private final TeamsTranscriptIngestService ingestService;
    private final MeetingApi meetingApi;
    private final FixedTenantContext tenantContext;
    private final SubscriptionStore subscriptionStore;
    private final ConcurrentLinkedQueue<PollTarget> queue = new ConcurrentLinkedQueue<>();
    private final Set<String> queuedKeys = ConcurrentHashMap.newKeySet();

    public TeamsTranscriptPollScheduler(
            TeamsTranscriptIngestService ingestService,
            MeetingApi meetingApi,
            FixedTenantContext tenantContext,
            SubscriptionStore subscriptionStore
    ) {
        this.ingestService = Objects.requireNonNull(ingestService);
        this.meetingApi = Objects.requireNonNull(meetingApi);
        this.tenantContext = Objects.requireNonNull(tenantContext);
        this.subscriptionStore = Objects.requireNonNull(subscriptionStore);
    }

    public void onMeetingOccurrenceUpserted(EventEnvelope envelope) {
        if (!MeetingOccurrenceContracts.MEETING_OCCURRENCE_UPSERTED.equals(envelope.eventType())) {
            return;
        }
        MeetingOccurrenceContracts.MeetingOccurrenceUpsertedPayload payload =
                com.nanobaseai.actenora.transcript.infrastructure.messaging.MeetingOccurrenceUpsertedHandler
                        .parse(envelope.payloadJson());
        tenantContext.use(TenantId.of(payload.tenantId()), CalendarMeetingUpsertAdapter.SYSTEM_ACTOR);
        try {
            MeetingResponse meeting = meetingApi.getMeeting(payload.meetingOccurrenceId());
            if (isReadyForTranscriptPoll(meeting, Instant.now())) {
                enqueue(payload.tenantId(), payload.meetingOccurrenceId());
            }
        } catch (RuntimeException ex) {
            log.debug("Skip transcript enqueue for meetingOccurrenceId={}: {}",
                    payload.meetingOccurrenceId(), ex.getMessage());
        }
    }

    public void runScheduledFallback(Instant now) {
        for (UUID tenantId : distinctTenantIds()) {
            tenantContext.use(TenantId.of(tenantId), CalendarMeetingUpsertAdapter.SYSTEM_ACTOR);
            MeetingListResponse page = meetingApi.listMeetings(new CursorPageRequest(null, null, null, 100));
            for (MeetingResponse meeting : page.items()) {
                if (isReadyForTranscriptPoll(meeting, now)) {
                    enqueue(tenantId, meeting.id());
                }
            }
        }
        drainQueue();
    }

    public void drainQueue() {
        PollTarget target;
        while ((target = queue.poll()) != null) {
            queuedKeys.remove(key(target.tenantId(), target.meetingOccurrenceId()));
            try {
                ingestService.pollMeeting(TenantId.of(target.tenantId()), target.meetingOccurrenceId());
            } catch (RuntimeException ex) {
                log.warn("Transcript poll failed tenantId={} meetingId={}: {}",
                        target.tenantId(), target.meetingOccurrenceId(), ex.getMessage());
            }
        }
    }

    private void enqueue(UUID tenantId, UUID meetingOccurrenceId) {
        String key = key(tenantId, meetingOccurrenceId);
        if (queuedKeys.add(key)) {
            queue.add(new PollTarget(tenantId, meetingOccurrenceId));
        }
    }

    static boolean isReadyForTranscriptPoll(MeetingResponse meeting, Instant now) {
        if (meeting.scheduledEndAt() != null && meeting.scheduledEndAt().isBefore(now)) {
            return hasTeamsIdentity(meeting);
        }
        return meeting.actualEndAt() != null && hasTeamsIdentity(meeting);
    }

    private static boolean hasTeamsIdentity(MeetingResponse meeting) {
        return StringUtils.hasText(meeting.teamsMeetingId()) || StringUtils.hasText(meeting.joinWebUrl());
    }

    private Set<UUID> distinctTenantIds() {
        return new java.util.LinkedHashSet<>(subscriptionStore.distinctTenantIds());
    }

    private static String key(UUID tenantId, UUID meetingOccurrenceId) {
        return tenantId + ":" + meetingOccurrenceId;
    }

    private record PollTarget(UUID tenantId, UUID meetingOccurrenceId) {
    }
}
