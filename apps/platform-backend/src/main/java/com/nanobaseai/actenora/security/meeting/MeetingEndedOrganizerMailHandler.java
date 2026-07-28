package com.nanobaseai.actenora.security.meeting;

import com.nanobaseai.actenora.delivery.api.DeliveryApi;
import com.nanobaseai.actenora.delivery.application.model.MeetingEndedMailBody;
import com.nanobaseai.actenora.delivery.application.worker.DeliveryWorker;
import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.meeting.api.dto.ParticipantResponse;
import com.nanobaseai.actenora.meeting.api.event.MeetingIntegrationEvents;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * On {@code meeting.MeetingEnded.v1}: email the organizer that note generation has started,
 * with a working deep link to the meeting detail page.
 */
public final class MeetingEndedOrganizerMailHandler {

    private static final Logger log = LoggerFactory.getLogger(MeetingEndedOrganizerMailHandler.class);

    private static final Pattern TENANT_ID = Pattern.compile("\"tenantId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MEETING_OCCURRENCE_ID =
            Pattern.compile("\"meetingOccurrenceId\"\\s*:\\s*\"([^\"]+)\"");

    private static final DateTimeFormatter WHEN_FMT = DateTimeFormatter
            .ofPattern("d MMMM yyyy · HH:mm", Locale.forLanguageTag("tr"))
            .withZone(ZoneId.of("Europe/Istanbul"));

    private final MeetingApi meetingApi;
    private final DeliveryApi deliveryApi;
    private final Optional<DeliveryWorker> deliveryWorker;
    private final String portalBaseUrl;

    public MeetingEndedOrganizerMailHandler(
            MeetingApi meetingApi,
            DeliveryApi deliveryApi,
            Optional<DeliveryWorker> deliveryWorker,
            String portalBaseUrl
    ) {
        this.meetingApi = Objects.requireNonNull(meetingApi, "meetingApi");
        this.deliveryApi = Objects.requireNonNull(deliveryApi, "deliveryApi");
        this.deliveryWorker = deliveryWorker == null ? Optional.empty() : deliveryWorker;
        this.portalBaseUrl = portalBaseUrl == null || portalBaseUrl.isBlank()
                ? "https://portal.nanobase.ai/easymeeting"
                : portalBaseUrl.trim();
    }

    public void handle(EventEnvelope envelope) {
        if (!MeetingIntegrationEvents.MEETING_ENDED.equals(envelope.eventType())) {
            return;
        }
        Payload payload = parse(envelope.payloadJson());
        try {
            MeetingResponse meeting = meetingApi.getMeeting(payload.meetingOccurrenceId());
            String organizerEmail = resolveOrganizerEmail(meeting);
            if (organizerEmail == null || organizerEmail.isBlank()) {
                log.info(
                        "Meeting-ended mail skipped: no organizer email meetingId={}",
                        payload.meetingOccurrenceId()
                );
                return;
            }

            String title = meeting.title() == null || meeting.title().isBlank()
                    ? "Toplantı"
                    : meeting.title();
            String whenLabel = formatWhen(meeting);
            String meetingUrl = MeetingEndedMailBody.meetingDetailUrl(portalBaseUrl, meeting.id());
            MeetingEndedMailBody body = new MeetingEndedMailBody(title, whenLabel, meetingUrl, meeting.id());

            deliveryApi.enqueueMeetingEndedOrganizerNotification(
                    payload.tenantId(),
                    meeting.id(),
                    organizerEmail,
                    organizerEmail,
                    "Toplantınız bitti · Rapor hazırlanıyor — " + title,
                    body.encode()
            );
            deliveryWorker.ifPresent(worker -> {
                try {
                    worker.pollOnce();
                } catch (RuntimeException ignored) {
                    /* scheduled worker will retry */
                }
            });
        } catch (RuntimeException ex) {
            // Mail must not fail MeetingEnded consumption permanently.
            log.warn(
                    "Meeting-ended organizer mail failed meetingId={}: {}",
                    payload.meetingOccurrenceId(),
                    ex.toString()
            );
        }
    }

    static Payload parse(String payloadJson) {
        return new Payload(
                UUID.fromString(requireField(TENANT_ID, payloadJson, "tenantId")),
                UUID.fromString(requireField(MEETING_OCCURRENCE_ID, payloadJson, "meetingOccurrenceId"))
        );
    }

    private String resolveOrganizerEmail(MeetingResponse meeting) {
        List<ParticipantResponse> participants = meetingApi.listParticipants(meeting.id());
        return participants.stream()
                .filter(p -> p.participantType() != null
                        && "ORGANIZER".equalsIgnoreCase(p.participantType().name()))
                .map(ParticipantResponse::email)
                .filter(email -> email != null && !email.isBlank())
                .findFirst()
                .orElseGet(() -> participants.stream()
                        .filter(p -> meeting.organizerUserId() != null
                                && p.entraUserId() != null
                                && meeting.organizerUserId().toString().equalsIgnoreCase(p.entraUserId()))
                        .map(ParticipantResponse::email)
                        .filter(email -> email != null && !email.isBlank())
                        .findFirst()
                        .orElseGet(() -> participants.stream()
                                .map(ParticipantResponse::email)
                                .filter(email -> email != null && !email.isBlank())
                                .findFirst()
                                .orElse(null)));
    }

    private static String formatWhen(MeetingResponse meeting) {
        if (meeting.scheduledStartAt() == null) {
            return "";
        }
        String start = WHEN_FMT.format(meeting.scheduledStartAt());
        if (meeting.scheduledEndAt() == null) {
            return start;
        }
        String endTime = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("tr"))
                .withZone(ZoneId.of("Europe/Istanbul"))
                .format(meeting.scheduledEndAt());
        return start + "–" + endTime;
    }

    private static String requireField(Pattern pattern, String json, String field) {
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return matcher.group(1);
    }

    record Payload(UUID tenantId, UUID meetingOccurrenceId) {
    }
}
