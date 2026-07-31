package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.api.dto.BusinessContextResponse;
import com.nanobaseai.actenora.meeting.api.dto.CreateMeetingRequest;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.meeting.api.dto.SyncInviteesRequest;
import com.nanobaseai.actenora.meeting.api.dto.UpdateMeetingRequest;
import com.nanobaseai.actenora.meeting.application.port.MeetingOccurrenceRepository;
import com.nanobaseai.actenora.meeting.domain.exception.DuplicateGraphIdentityException;
import com.nanobaseai.actenora.meeting.infrastructure.tenancy.FixedTenantContext;
import com.nanobaseai.actenora.meetingintelligence.api.ledger.ContinuityLedgerApi;
import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarEvent;
import com.nanobaseai.actenora.microsoftconnection.application.model.ParticipantMetadata;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Upserts {@link MeetingResponse} rows from Graph {@link CalendarEvent} snapshots
 * and links continuity projections for prior-meeting carry-over.
 *
 * <p>Also advances occurrence lifecycle (DRAFT → SCHEDULED → … → ENDED) from schedule/clock
 * and applies CANCELLED when Graph marks the event cancelled.
 */
public final class CalendarMeetingUpsertAdapter {

    private static final Logger log = LoggerFactory.getLogger(CalendarMeetingUpsertAdapter.class);

    public static final UUID SYSTEM_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final MeetingApi meetingApi;
    private final FixedTenantContext tenantContext;
    private final ContinuityLedgerApi continuityLedgerApi;
    private final MeetingOccurrenceRepository occurrenceRepository;

    public CalendarMeetingUpsertAdapter(MeetingApi meetingApi, FixedTenantContext tenantContext) {
        this(meetingApi, tenantContext, null, null);
    }

    public CalendarMeetingUpsertAdapter(
            MeetingApi meetingApi,
            FixedTenantContext tenantContext,
            ContinuityLedgerApi continuityLedgerApi,
            MeetingOccurrenceRepository occurrenceRepository
    ) {
        this.meetingApi = Objects.requireNonNull(meetingApi, "meetingApi");
        this.tenantContext = Objects.requireNonNull(tenantContext, "tenantContext");
        this.continuityLedgerApi = continuityLedgerApi;
        this.occurrenceRepository = occurrenceRepository;
    }

    public void upsertEvents(TenantId tenantId, List<CalendarEvent> events) {
        Objects.requireNonNull(tenantId, "tenantId");
        if (events == null || events.isEmpty()) {
            return;
        }
        tenantContext.use(tenantId, SYSTEM_ACTOR);
        UUID businessContextId = resolveBusinessContextId();
        for (CalendarEvent event : events) {
            if (event.cancelled()) {
                cancelIfPresent(event);
                continue;
            }
            upsertOne(tenantId, businessContextId, event);
        }
    }

    private UUID resolveBusinessContextId() {
        List<BusinessContextResponse> contexts = meetingApi.listBusinessContexts();
        if (contexts.isEmpty()) {
            throw new ActenoraException(
                    "GRAPH_BUSINESS_CONTEXT_REQUIRED",
                    "No business context exists for tenant; create one via POST /api/v1/meetings/business-contexts");
        }
        return contexts.getFirst().id();
    }

    private void cancelIfPresent(CalendarEvent event) {
        String graphId = event.immutableIdentity().graphEventImmutableId();
        Optional<MeetingResponse> existing = meetingApi.findByGraphEventImmutableId(graphId);
        if (existing.isEmpty()) {
            log.debug("Ignoring cancelled Graph event with no local meeting graphEventImmutableId={}", graphId);
            return;
        }
        try {
            MeetingResponse advanced = meetingApi.advanceMeetingLifecycle(existing.get().id(), true);
            log.info(
                    "Cancelled meeting from Graph calendar event meetingId={} status={}",
                    advanced.id(),
                    advanced.status()
            );
        } catch (RuntimeException ex) {
            log.warn(
                    "Lifecycle cancel failed for meetingId={} reason={}",
                    existing.get().id(),
                    ex.getMessage()
            );
        }
    }

    private void upsertOne(TenantId tenantId, UUID businessContextId, CalendarEvent event) {
        String graphId = event.immutableIdentity().graphEventImmutableId();
        CreateMeetingRequest create = toCreateRequest(businessContextId, event);
        MeetingResponse meeting;
        try {
            meeting = meetingApi.createMeeting(create);
        } catch (DuplicateGraphIdentityException ex) {
            MeetingResponse existing = meetingApi.findByGraphEventImmutableId(graphId)
                    .orElseThrow(() -> ex);
            meeting = meetingApi.updateMeeting(existing.id(), toUpdateRequest(event, existing.version()));
            log.debug("Updated meeting from Graph calendar event graphEventImmutableId={}", graphId);
        }
        meeting = advanceLifecycleQuietly(meeting);
        syncInviteesQuietly(meeting.id(), event);
        linkContinuity(tenantId, businessContextId, meeting);
    }

    private void syncInviteesQuietly(UUID meetingId, CalendarEvent event) {
        List<CreateMeetingRequest.ParticipantInput> invitees = mapParticipants(event.attendees());
        if (invitees.isEmpty()) {
            return;
        }
        try {
            meetingApi.syncInvitees(meetingId, new SyncInviteesRequest(invitees));
        } catch (RuntimeException ex) {
            log.warn(
                    "Invitee sync failed for meetingId={} attendeeCount={} reason={}",
                    meetingId,
                    invitees.size(),
                    ex.getMessage()
            );
        }
    }

    private MeetingResponse advanceLifecycleQuietly(MeetingResponse meeting) {
        try {
            MeetingResponse advanced = meetingApi.advanceMeetingLifecycle(meeting.id(), false);
            if (advanced.status() != meeting.status()) {
                log.info(
                        "Advanced meeting lifecycle meetingId={} {} -> {}",
                        advanced.id(),
                        meeting.status(),
                        advanced.status()
                );
            }
            return advanced;
        } catch (RuntimeException ex) {
            log.warn(
                    "Lifecycle advance failed for meetingId={} reason={}",
                    meeting.id(),
                    ex.getMessage()
            );
            return meeting;
        }
    }

    private void linkContinuity(TenantId tenantId, UUID businessContextId, MeetingResponse meeting) {
        if (continuityLedgerApi == null || occurrenceRepository == null || meeting.meetingSeriesId() == null) {
            return;
        }
        try {
            UUID previousId = occurrenceRepository.findPreviousInSeries(
                            tenantId,
                            meeting.meetingSeriesId(),
                            meeting.scheduledStartAt(),
                            meeting.id()
                    )
                    .map(o -> o.id())
                    .orElse(null);
            continuityLedgerApi.linkContinuity(
                    tenantId,
                    meeting.id(),
                    meeting.meetingSeriesId(),
                    businessContextId,
                    previousId
            );
        } catch (RuntimeException ex) {
            log.warn(
                    "Continuity link failed for meetingOccurrenceId={} reason={}",
                    meeting.id(),
                    ex.getMessage()
            );
        }
    }

    static CreateMeetingRequest toCreateRequest(UUID businessContextId, CalendarEvent event) {
        return new CreateMeetingRequest(
                businessContextId,
                null,
                event.seriesMasterIdOptional().orElse(null),
                event.immutableIdentity().graphEventImmutableId(),
                event.iCalUIdOptional().orElse(null),
                event.originalStartAtOptional().orElse(event.startAt()),
                event.onlineMeetingIdOptional().orElse(null),
                null,
                event.joinWebUrlOptional().orElse(null),
                event.subject(),
                null,
                event.startAt(),
                event.endAt(),
                null,
                mapParticipants(event.attendees())
        );
    }

    static UpdateMeetingRequest toUpdateRequest(CalendarEvent event, long expectedVersion) {
        return new UpdateMeetingRequest(
                event.subject(),
                event.startAt(),
                event.endAt(),
                event.immutableIdentity().graphEventImmutableId(),
                event.iCalUIdOptional().orElse(null),
                event.originalStartAtOptional().orElse(event.startAt()),
                event.onlineMeetingIdOptional().orElse(null),
                null,
                event.joinWebUrlOptional().orElse(null),
                null,
                expectedVersion
        );
    }

    private static List<CreateMeetingRequest.ParticipantInput> mapParticipants(List<ParticipantMetadata> attendees) {
        if (attendees == null || attendees.isEmpty()) {
            return List.of();
        }
        return attendees.stream()
                .map(p -> new CreateMeetingRequest.ParticipantInput(
                        p.id(),
                        p.displayName(),
                        p.emailOptional().orElse(null),
                        p.role(),
                        false
                ))
                .toList();
    }
}
