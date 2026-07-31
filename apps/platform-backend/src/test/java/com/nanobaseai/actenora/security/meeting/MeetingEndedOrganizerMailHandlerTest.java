package com.nanobaseai.actenora.security.meeting;

import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.delivery.api.DeliveryApi;
import com.nanobaseai.actenora.delivery.api.DeliveryOrderView;
import com.nanobaseai.actenora.delivery.api.DeliveryRequestId;
import com.nanobaseai.actenora.delivery.api.DeliveryRequestStatusView;
import com.nanobaseai.actenora.delivery.application.EnqueueDeliveryCommand;
import com.nanobaseai.actenora.delivery.application.EnqueueDeliveryResult;
import com.nanobaseai.actenora.delivery.application.model.MeetingEndedMailBody;
import com.nanobaseai.actenora.delivery.domain.DeliveryStatus;
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
import com.nanobaseai.actenora.meeting.api.dto.UpdateBusinessContextRequest;
import com.nanobaseai.actenora.meeting.api.dto.UpdateMeetingRequest;
import com.nanobaseai.actenora.meeting.api.event.MeetingIntegrationEvents;
import com.nanobaseai.actenora.meeting.domain.model.AttendanceStatus;
import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrenceStatus;
import com.nanobaseai.actenora.meeting.domain.model.ParticipantType;
import com.nanobaseai.actenora.meeting.domain.model.ProcessingPriority;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeetingEndedOrganizerMailHandlerTest {

    @Test
    void enqueuesOrganizerMailWithMeetingDetailDeepLink() {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID meetingId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Instant start = Instant.parse("2026-07-28T07:00:00Z");
        Instant end = Instant.parse("2026-07-28T08:00:00Z");

        FakeMeetingApi meetingApi = new FakeMeetingApi(new MeetingResponse(
                meetingId,
                tenantId,
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                null,
                "Q3 Ürün Planlama",
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
        ), List.of(new ParticipantResponse(
                UUID.randomUUID(),
                meetingId,
                "oid-1",
                "Organizer",
                "org@example.com",
                ParticipantType.ORGANIZER,
                AttendanceStatus.INVITED,
                start,
                end,
                false
        )));

        RecordingDeliveryApi deliveryApi = new RecordingDeliveryApi();
        MeetingEndedOrganizerMailHandler handler = new MeetingEndedOrganizerMailHandler(
                meetingApi,
                deliveryApi,
                Optional.empty(),
                "https://portal.nanobase.ai/easymeeting/"
        );

        String payload = "{"
                + "\"eventId\":\"" + UUID.randomUUID() + "\","
                + "\"occurredAt\":\"" + end + "\","
                + "\"tenantId\":\"" + tenantId + "\","
                + "\"meetingOccurrenceId\":\"" + meetingId + "\","
                + "\"actualEndAt\":\"" + end + "\""
                + "}";
        handler.handle(new EventEnvelope(
                UUID.randomUUID(),
                MeetingIntegrationEvents.MEETING_ENDED,
                1,
                end,
                TenantId.of(tenantId),
                "MeetingOccurrence",
                meetingId.toString(),
                UUID.randomUUID(),
                null,
                null,
                "meeting",
                payload
        ));

        assertEquals(1, deliveryApi.calls.size());
        RecordingDeliveryApi.Call call = deliveryApi.calls.getFirst();
        assertEquals(tenantId, call.tenantId());
        assertEquals(meetingId, call.meetingOccurrenceId());
        assertEquals("org@example.com", call.recipientEmail());
        assertTrue(call.subject().contains("Q3 Ürün Planlama"));
        assertTrue(call.bodyText().contains(
                "https://portal.nanobase.ai/easymeeting/meetings/" + meetingId));
        MeetingEndedMailBody decoded = MeetingEndedMailBody.decode(call.bodyText());
        assertEquals(
                "https://portal.nanobase.ai/easymeeting/meetings/" + meetingId,
                decoded.meetingUrl()
        );
    }

    private static final class FakeMeetingApi implements MeetingApi {
        private final MeetingResponse meeting;
        private final List<ParticipantResponse> participants;

        private FakeMeetingApi(MeetingResponse meeting, List<ParticipantResponse> participants) {
            this.meeting = meeting;
            this.participants = participants;
        }

        @Override
        public MeetingResponse getMeeting(UUID meetingId) {
            return meeting;
        }

        @Override
        public List<ParticipantResponse> listParticipants(UUID meetingId) {
            return participants;
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
        public Optional<MeetingResponse> findByGraphEventImmutableId(String graphEventImmutableId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MeetingListResponse listMeetings(CursorPageRequest pageRequest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<MeetingResponse> searchMeetings(
                String query,
                com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrenceStatus status,
                int limit
        ) {
            throw new UnsupportedOperationException();
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
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ParticipantResponse> applyAttendance(UUID meetingId, ApplyAttendanceRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ParticipantResponse> syncInvitees(
                UUID meetingId,
                com.nanobaseai.actenora.meeting.api.dto.SyncInviteesRequest request
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BusinessContextResponse createBusinessContext(CreateBusinessContextRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<BusinessContextResponse> listBusinessContexts() {
            throw new UnsupportedOperationException();
        }

        @Override
        public BusinessContextResponse updateBusinessContext(UUID id, UpdateBusinessContextRequest request) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingDeliveryApi implements DeliveryApi {
        private final List<Call> calls = new ArrayList<>();

        record Call(
                UUID tenantId,
                UUID meetingOccurrenceId,
                String recipientEmail,
                String subject,
                String bodyText
        ) {
        }

        @Override
        public EnqueueDeliveryResult enqueueMeetingEndedOrganizerNotification(
                UUID tenantId,
                UUID meetingOccurrenceId,
                String recipientEmail,
                String recipientDisplayName,
                String subject,
                String bodyText
        ) {
            calls.add(new Call(tenantId, meetingOccurrenceId, recipientEmail, subject, bodyText));
            return new EnqueueDeliveryResult(
                    List.of(DeliveryRequestId.of(UUID.randomUUID())),
                    List.of()
            );
        }

        @Override
        public DeliveryOrderView requestExternalDelivery(
                UUID tenantId, ApprovalId approvalId, UUID noteVersionId, String channel) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<DeliveryOrderView> getOrder(UUID tenantId, UUID orderId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EnqueueDeliveryResult enqueue(EnqueueDeliveryCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<DeliveryStatus> status(TenantId tenantId, DeliveryRequestId id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DeliveryStatus confirmDelivered(TenantId tenantId, DeliveryRequestId id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DeliveryRequestId resolveByProviderMessageId(TenantId tenantId, String providerMessageId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EnqueueDeliveryResult enqueueDraftOrganizerNotification(
                UUID tenantId, UUID noteVersionId, String recipientEmail,
                String recipientDisplayName, String subject, String bodyText) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DeliveryRequestStatusView> listByNoteVersion(UUID tenantId, UUID noteVersionId) {
            throw new UnsupportedOperationException();
        }
    }
}
