package com.nanobaseai.actenora.security.messaging;

import com.nanobaseai.actenora.aiprocessing.api.AiProcessingApi;
import com.nanobaseai.actenora.aiprocessing.application.port.AdmissionController;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.transcript.api.event.TranscriptIntegrationEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Submits AI extraction jobs when {@code transcript.TranscriptReady.v1} is consumed.
 */
public final class TranscriptReadyAiAdmissionHandler {

    private static final Logger log = LoggerFactory.getLogger(TranscriptReadyAiAdmissionHandler.class);

    private static final Pattern TENANT_ID = Pattern.compile("\"tenantId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TRANSCRIPT_ID = Pattern.compile("\"transcriptId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MEETING_OCCURRENCE_ID =
            Pattern.compile("\"meetingOccurrenceId\"\\s*:\\s*\"([^\"]+)\"");

    private static final String TASK_TYPE = "CHUNK_EXTRACTION";
    private static final String PROMPT_VERSION = "pv-meeting-chunk-extraction-v1";
    private static final String SCHEMA_VERSION = "sv-meeting-chunk-extraction-v1";

    private final AiProcessingApi aiProcessingApi;

    public TranscriptReadyAiAdmissionHandler(AiProcessingApi aiProcessingApi) {
        this.aiProcessingApi = Objects.requireNonNull(aiProcessingApi, "aiProcessingApi");
    }

    public void handle(EventEnvelope envelope) {
        if (!TranscriptIntegrationEvents.TRANSCRIPT_READY.equals(envelope.eventType())) {
            return;
        }
        TranscriptIntegrationEvents.TranscriptReady payload = parse(envelope.payloadJson());
        AdmissionController.SubmitAiJobCommand command = new AdmissionController.SubmitAiJobCommand(
                payload.tenantId(),
                payload.meetingOccurrenceId(),
                payload.transcriptId(),
                TASK_TYPE,
                JobPriority.NORMAL,
                AiCapability.TRANSCRIPT_EXTRACTION,
                PROMPT_VERSION,
                SCHEMA_VERSION,
                null,
                0,
                null,
                payload.eventId(),
                payload.occurredAt() == null ? Instant.now() : payload.occurredAt()
        );
        AdmissionController.AdmissionDecision decision = aiProcessingApi.submitJob(command);
        if (!decision.admitted()) {
            log.warn(
                    "TranscriptReady AI admission rejected tenantId={} transcriptId={} reason={}",
                    payload.tenantId(),
                    payload.transcriptId(),
                    decision.rejectReason());
            return;
        }
        log.info(
                "TranscriptReady admitted AI job tenantId={} meetingOccurrenceId={} transcriptId={} jobId={}",
                payload.tenantId(),
                payload.meetingOccurrenceId(),
                payload.transcriptId(),
                decision.job().id());
    }

    static TranscriptIntegrationEvents.TranscriptReady parse(String payloadJson) {
        UUID tenantId = UUID.fromString(requireField(TENANT_ID, payloadJson, "tenantId"));
        UUID transcriptId = UUID.fromString(requireField(TRANSCRIPT_ID, payloadJson, "transcriptId"));
        UUID meetingOccurrenceId =
                UUID.fromString(requireField(MEETING_OCCURRENCE_ID, payloadJson, "meetingOccurrenceId"));
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        int segmentCount = 0;
        Matcher segmentMatcher = Pattern.compile("\"segmentCount\"\\s*:\\s*(\\d+)").matcher(payloadJson);
        if (segmentMatcher.find()) {
            segmentCount = Integer.parseInt(segmentMatcher.group(1));
        }
        Matcher eventIdMatcher = Pattern.compile("\"eventId\"\\s*:\\s*\"([^\"]+)\"").matcher(payloadJson);
        if (eventIdMatcher.find()) {
            eventId = UUID.fromString(eventIdMatcher.group(1));
        }
        Matcher occurredMatcher = Pattern.compile("\"occurredAt\"\\s*:\\s*\"([^\"]+)\"").matcher(payloadJson);
        if (occurredMatcher.find()) {
            occurredAt = Instant.parse(occurredMatcher.group(1));
        }
        return new TranscriptIntegrationEvents.TranscriptReady(
                eventId, occurredAt, tenantId, transcriptId, meetingOccurrenceId, segmentCount);
    }

    private static String requireField(Pattern pattern, String json, String field) {
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return matcher.group(1);
    }
}
