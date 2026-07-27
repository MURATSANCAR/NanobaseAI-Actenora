package com.nanobaseai.actenora.security.messaging;

import com.nanobaseai.actenora.aiprocessing.api.AiProcessingApi;
import com.nanobaseai.actenora.aiprocessing.application.port.AdmissionController;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;
import com.nanobaseai.actenora.aiprocessing.domain.prompt.OutputLanguagePolicy;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.tenant.api.TenantApi;
import com.nanobaseai.actenora.tenant.api.TenantView;
import com.nanobaseai.actenora.transcript.api.event.TranscriptIntegrationEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Submits AI extraction jobs when {@code transcript.TranscriptReady.v1} is consumed.
 * Output language: transcript language → tenant default_language → {@code tr}.
 */
public final class TranscriptReadyAiAdmissionHandler {

    private static final Logger log = LoggerFactory.getLogger(TranscriptReadyAiAdmissionHandler.class);

    private static final Pattern TENANT_ID = Pattern.compile("\"tenantId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TRANSCRIPT_ID = Pattern.compile("\"transcriptId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MEETING_OCCURRENCE_ID =
            Pattern.compile("\"meetingOccurrenceId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern LANGUAGE = Pattern.compile("\"language\"\\s*:\\s*\"([^\"]+)\"");

    private static final String TASK_TYPE = "CHUNK_EXTRACTION";
    private static final String PROMPT_VERSION = "pv-meeting-chunk-extraction-v1";
    private static final String SCHEMA_VERSION = "extraction-output.v1";
    /** Meetings at/above this segment count use BULK SLA (240m) instead of NORMAL (60m). */
    static final int BULK_SEGMENT_THRESHOLD = 100;

    private final AiProcessingApi aiProcessingApi;
    private final Function<UUID, Optional<String>> tenantDefaultLanguage;

    public TranscriptReadyAiAdmissionHandler(AiProcessingApi aiProcessingApi) {
        this(aiProcessingApi, tenantId -> Optional.empty());
    }

    public TranscriptReadyAiAdmissionHandler(AiProcessingApi aiProcessingApi, TenantApi tenantApi) {
        this(
                aiProcessingApi,
                tenantId -> Objects.requireNonNull(tenantApi, "tenantApi")
                        .findById(TenantId.of(tenantId))
                        .map(TenantView::defaultLanguage)
        );
    }

    public TranscriptReadyAiAdmissionHandler(
            AiProcessingApi aiProcessingApi,
            Function<UUID, Optional<String>> tenantDefaultLanguage
    ) {
        this.aiProcessingApi = Objects.requireNonNull(aiProcessingApi, "aiProcessingApi");
        this.tenantDefaultLanguage = Objects.requireNonNull(tenantDefaultLanguage, "tenantDefaultLanguage");
    }

    public void handle(EventEnvelope envelope) {
        if (!TranscriptIntegrationEvents.TRANSCRIPT_READY.equals(envelope.eventType())) {
            return;
        }
        TranscriptIntegrationEvents.TranscriptReady payload = parse(envelope.payloadJson());
        String language = resolveLanguage(payload);
        JobPriority priority = priorityForSegmentCount(payload.segmentCount());
        AdmissionController.SubmitAiJobCommand command = new AdmissionController.SubmitAiJobCommand(
                payload.tenantId(),
                payload.meetingOccurrenceId(),
                payload.transcriptId(),
                TASK_TYPE,
                priority,
                AiCapability.TRANSCRIPT_EXTRACTION,
                PROMPT_VERSION,
                SCHEMA_VERSION,
                language,
                Math.max(0, payload.segmentCount()),
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
                "TranscriptReady admitted AI job tenantId={} meetingOccurrenceId={} transcriptId={} jobId={} language={} priority={} segmentCount={}",
                payload.tenantId(),
                payload.meetingOccurrenceId(),
                payload.transcriptId(),
                decision.job().id(),
                language,
                priority,
                payload.segmentCount());
    }

    static JobPriority priorityForSegmentCount(int segmentCount) {
        return segmentCount >= BULK_SEGMENT_THRESHOLD ? JobPriority.BULK : JobPriority.NORMAL;
    }

    String resolveLanguage(TranscriptIntegrationEvents.TranscriptReady payload) {
        String tenantDefault = tenantDefaultLanguage.apply(payload.tenantId()).orElse(null);
        return OutputLanguagePolicy.firstNonBlank(payload.language(), tenantDefault);
    }

    static TranscriptIntegrationEvents.TranscriptReady parse(String payloadJson) {
        UUID tenantId = UUID.fromString(requireField(TENANT_ID, payloadJson, "tenantId"));
        UUID transcriptId = UUID.fromString(requireField(TRANSCRIPT_ID, payloadJson, "transcriptId"));
        UUID meetingOccurrenceId =
                UUID.fromString(requireField(MEETING_OCCURRENCE_ID, payloadJson, "meetingOccurrenceId"));
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        int segmentCount = 0;
        String language = null;
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
        Matcher languageMatcher = LANGUAGE.matcher(payloadJson);
        if (languageMatcher.find()) {
            language = languageMatcher.group(1);
        }
        return new TranscriptIntegrationEvents.TranscriptReady(
                eventId, occurredAt, tenantId, transcriptId, meetingOccurrenceId, segmentCount, language);
    }

    private static String requireField(Pattern pattern, String json, String field) {
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return matcher.group(1);
    }
}
