package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.api.dto.CursorPageRequest;
import com.nanobaseai.actenora.meeting.api.dto.MeetingListResponse;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.meeting.infrastructure.tenancy.FixedTenantContext;
import com.nanobaseai.actenora.microsoftconnection.application.port.SubscriptionStore;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.ExponentialBackoff;
import com.nanobaseai.actenora.transcript.api.TranscriptApi;
import com.nanobaseai.actenora.transcript.api.contract.MeetingOccurrenceContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Event-driven durable Teams transcript polling worker.
 */
public final class TeamsTranscriptPollScheduler {

    private static final Logger log = LoggerFactory.getLogger(TeamsTranscriptPollScheduler.class);

    private final TeamsTranscriptIngestService ingestService;
    private final MeetingApi meetingApi;
    private final FixedTenantContext tenantContext;
    private final SubscriptionStore subscriptionStore;
    private final TranscriptApi transcriptApi;
    private final TranscriptPollWorkStore workStore;
    private final ExponentialBackoff backoff;
    private final int maxAttempts;
    private final Duration maxAge;
    private final Duration staleClaimAfter;
    private final int batchSize;
    private final GraphObservability observability;

    public TeamsTranscriptPollScheduler(
            TeamsTranscriptIngestService ingestService,
            MeetingApi meetingApi,
            FixedTenantContext tenantContext,
            SubscriptionStore subscriptionStore,
            TranscriptApi transcriptApi,
            TranscriptPollWorkStore workStore,
            ExponentialBackoff backoff,
            int maxAttempts,
            Duration maxAge,
            Duration staleClaimAfter,
            int batchSize,
            GraphObservability observability
    ) {
        this.ingestService = Objects.requireNonNull(ingestService);
        this.meetingApi = Objects.requireNonNull(meetingApi);
        this.tenantContext = Objects.requireNonNull(tenantContext);
        this.subscriptionStore = Objects.requireNonNull(subscriptionStore);
        this.transcriptApi = Objects.requireNonNull(transcriptApi);
        this.workStore = Objects.requireNonNull(workStore);
        this.backoff = Objects.requireNonNull(backoff);
        if (maxAttempts < 1 || batchSize < 1) {
            throw new IllegalArgumentException("maxAttempts and batchSize must be positive");
        }
        this.maxAttempts = maxAttempts;
        this.maxAge = Objects.requireNonNull(maxAge);
        this.staleClaimAfter = Objects.requireNonNull(staleClaimAfter);
        this.batchSize = batchSize;
        this.observability = observability;
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
                workStore.enqueue(payload.tenantId(), payload.meetingOccurrenceId(), Instant.now());
            }
        } catch (RuntimeException ex) {
            log.debug("Skip transcript enqueue for meetingOccurrenceId={}: {}",
                    payload.meetingOccurrenceId(), ex.getMessage());
        }
    }

    public void runScheduledFallback(Instant now) {
        for (UUID tenantId : distinctTenantIds()) {
            tenantContext.use(TenantId.of(tenantId), CalendarMeetingUpsertAdapter.SYSTEM_ACTOR);
            String cursor = null;
            do {
                MeetingListResponse page = meetingApi.listMeetings(new CursorPageRequest(null, null, cursor, 100));
                for (MeetingResponse meeting : page.items()) {
                    if (isReadyForTranscriptPoll(meeting, now)
                            && needsPoll(meeting, TenantId.of(tenantId))) {
                        workStore.enqueue(tenantId, meeting.id(), now);
                    }
                }
                cursor = page.nextCursor();
            } while (StringUtils.hasText(cursor));
        }
        drainDue(now);
        if (observability != null) {
            long oldestSeconds = workStore.oldestPendingCreatedAt()
                    .map(created -> Math.max(0L, Duration.between(created, now).toSeconds()))
                    .orElse(0L);
            observability.updateTranscriptGauges(workStore.countPending(), oldestSeconds);
        }
    }

    public void drainDue(Instant now) {
        for (TranscriptPollWorkStore.WorkItem target : workStore.claimDue(now, batchSize, staleClaimAfter)) {
            int attempt = target.attemptCount() + 1;
            try {
                TenantId tenantId = TenantId.of(target.tenantId());
                TeamsTranscriptIngestService.PollResult result =
                        ingestService.pollMeeting(tenantId, target.meetingOccurrenceId());
                if (result == TeamsTranscriptIngestService.PollResult.CONFIGURATION_MISSING) {
                    workStore.deadLetter(
                            target.tenantId(), target.meetingOccurrenceId(), attempt,
                            "GRAPH_MAILBOX_CONFIGURATION_MISSING", now);
                    recordPoll("configuration_missing");
                    continue;
                }
                boolean transcriptOk = result == TeamsTranscriptIngestService.PollResult.INGESTED
                        || result == TeamsTranscriptIngestService.PollResult.ALREADY_INGESTED;
                boolean attendanceOk;
                try {
                    tenantContext.use(tenantId, CalendarMeetingUpsertAdapter.SYSTEM_ACTOR);
                    attendanceOk = !needsAttendanceRefresh(meetingApi.getMeeting(target.meetingOccurrenceId()));
                } catch (RuntimeException ex) {
                    attendanceOk = false;
                }
                if (transcriptOk && attendanceOk) {
                    workStore.complete(target.tenantId(), target.meetingOccurrenceId(), now);
                    recordPoll(result.name().toLowerCase());
                } else {
                    String code = transcriptOk ? "ATTENDANCE_PENDING" : "TRANSCRIPT_NOT_AVAILABLE";
                    rescheduleOrDeadLetter(target, attempt, code, now);
                    recordPoll(transcriptOk ? "attendance_pending" : "not_available");
                }
            } catch (RuntimeException ex) {
                log.warn("Transcript poll failed tenantId={} meetingId={}: {}",
                        target.tenantId(), target.meetingOccurrenceId(), ex.getMessage());
                rescheduleOrDeadLetter(target, attempt, ex.getClass().getSimpleName(), now);
                recordPoll("failed");
            }
        }
    }

    /**
     * Ended Teams meetings still need polling when transcript is missing <strong>or</strong>
     * attendance was never applied (all invitees stuck in INVITED/ACCEPTED/TENTATIVE).
     */
    boolean needsPoll(MeetingResponse meeting, TenantId tenantId) {
        if (!transcriptApi.hasTranscriptForMeeting(tenantId, meeting.id())) {
            return true;
        }
        return needsAttendanceRefresh(meeting);
    }

    private boolean needsAttendanceRefresh(MeetingResponse meeting) {
        try {
            var participants = meetingApi.listParticipants(meeting.id());
            if (participants.isEmpty()) {
                return true;
            }
            return participants.stream().noneMatch(p -> {
                var status = p.attendanceStatus();
                return status != null && (
                        "JOINED".equalsIgnoreCase(status.name())
                                || "LEFT".equalsIgnoreCase(status.name())
                                || "ABSENT".equalsIgnoreCase(status.name())
                );
            });
        } catch (RuntimeException ex) {
            log.debug("Attendance refresh check failed meetingId={}: {}", meeting.id(), ex.getMessage());
            return false;
        }
    }

    private void rescheduleOrDeadLetter(
            TranscriptPollWorkStore.WorkItem target,
            int attempt,
            String failureCode,
            Instant now
    ) {
        if (attempt >= maxAttempts || target.createdAt().plus(maxAge).isBefore(now)) {
            workStore.deadLetter(
                    target.tenantId(), target.meetingOccurrenceId(), attempt, failureCode, now);
            return;
        }
        workStore.reschedule(
                target.tenantId(),
                target.meetingOccurrenceId(),
                attempt,
                now.plus(backoff.delayForAttempt(attempt - 1)),
                failureCode,
                now);
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

    public long pendingCount() {
        return workStore.countPending();
    }

    private void recordPoll(String outcome) {
        if (observability != null) {
            observability.recordTranscriptPoll(outcome);
        }
    }
}
