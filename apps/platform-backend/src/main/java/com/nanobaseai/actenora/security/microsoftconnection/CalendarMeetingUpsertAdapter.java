package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.api.dto.BusinessContextResponse;
import com.nanobaseai.actenora.meeting.api.dto.CreateMeetingRequest;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.meeting.api.dto.UpdateMeetingRequest;
import com.nanobaseai.actenora.meeting.domain.exception.DuplicateGraphIdentityException;
import com.nanobaseai.actenora.meeting.infrastructure.tenancy.FixedTenantContext;
import com.nanobaseai.actenora.microsoftconnection.application.model.CalendarEvent;
import com.nanobaseai.actenora.microsoftconnection.application.model.ParticipantMetadata;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Upserts {@link MeetingResponse} rows from Graph {@link CalendarEvent} snapshots.
 * Requires at least one business context per tenant (operator-provisioned via Meeting API).
 */
public final class CalendarMeetingUpsertAdapter {

    private static final Logger log = LoggerFactory.getLogger(CalendarMeetingUpsertAdapter.class);

    public static final UUID SYSTEM_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final MeetingApi meetingApi;
    private final FixedTenantContext tenantContext;

    public CalendarMeetingUpsertAdapter(MeetingApi meetingApi, FixedTenantContext tenantContext) {
        this.meetingApi = Objects.requireNonNull(meetingApi, "meetingApi");
        this.tenantContext = Objects.requireNonNull(tenantContext, "tenantContext");
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
                continue;
            }
            upsertOne(businessContextId, event);
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

    private void upsertOne(UUID businessContextId, CalendarEvent event) {
        String graphId = event.immutableIdentity().graphEventImmutableId();
        CreateMeetingRequest create = toCreateRequest(businessContextId, event);
        try {
            meetingApi.createMeeting(create);
        } catch (DuplicateGraphIdentityException ex) {
            MeetingResponse existing = meetingApi.findByGraphEventImmutableId(graphId)
                    .orElseThrow(() -> ex);
            meetingApi.updateMeeting(existing.id(), toUpdateRequest(event, existing.version()));
            log.debug("Updated meeting from Graph calendar event graphEventImmutableId={}", graphId);
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
