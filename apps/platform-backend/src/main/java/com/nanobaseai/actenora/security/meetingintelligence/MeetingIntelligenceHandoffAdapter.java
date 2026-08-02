package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.aiprocessing.application.port.MeetingNoteHandoffPort;
import com.nanobaseai.actenora.aiprocessing.application.port.TranscriptSegmentSourcePort;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.delivery.api.DeliveryApi;
import com.nanobaseai.actenora.delivery.application.model.DraftMinutesReadyMailBody;
import com.nanobaseai.actenora.delivery.application.worker.DeliveryWorker;
import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.meetingintelligence.api.EvidenceValidationApi;
import com.nanobaseai.actenora.meetingintelligence.api.MeetingIntelligenceApi;
import com.nanobaseai.actenora.meetingintelligence.api.RunValidationCommand;
import com.nanobaseai.actenora.meetingintelligence.api.ValidationExecutionResult;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MapAiCandidatesCommand;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteDetailResponse;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingIntelligenceAuditPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.NoteArtifactStoragePort;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.QualityGateOutcome;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationParticipant;
import com.nanobaseai.actenora.security.notification.PlatformUserNotificationPublisher;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Platform adapter: quality-gate FinalNoteDraft, then map into Meeting Intelligence when allowed.
 *
 * <p>PASSED / PASSED_WITH_WARNINGS / MANUAL_REVIEW_REQUIRED → {@code mapAiCandidates} as DRAFT.
 * REJECTED → no note.
 *
 * <p>Draft organizer mail uses {@link DeliveryApi} ({@code DRAFT_ORGANIZER}) only — no JavaMail bypass.
 */
public final class MeetingIntelligenceHandoffAdapter implements MeetingNoteHandoffPort {

    private static final Logger log = LoggerFactory.getLogger(MeetingIntelligenceHandoffAdapter.class);

    private final MeetingIntelligenceApi meetingIntelligenceApi;
    private final EvidenceValidationApi evidenceValidationApi;
    private final TranscriptSegmentSourcePort segmentSource;
    private final MeetingIntelligenceAuditPort auditPort;
    private final Optional<MeetingApi> meetingApi;
    private final NoteArtifactStoragePort noteArtifactStorage;
    private final Optional<PlatformUserNotificationPublisher> notificationPublisher;
    private final Optional<DeliveryApi> deliveryApi;
    private final Optional<DeliveryWorker> deliveryWorker;
    private final String portalBaseUrl;

    private static final DateTimeFormatter WHEN_FMT = DateTimeFormatter
            .ofPattern("d MMMM yyyy · HH:mm", Locale.forLanguageTag("tr"))
            .withZone(ZoneId.of("Europe/Istanbul"));

    public MeetingIntelligenceHandoffAdapter(
            MeetingIntelligenceApi meetingIntelligenceApi,
            EvidenceValidationApi evidenceValidationApi,
            TranscriptSegmentSourcePort segmentSource,
            MeetingIntelligenceAuditPort auditPort
    ) {
        this(
                meetingIntelligenceApi,
                evidenceValidationApi,
                segmentSource,
                auditPort,
                Optional.empty(),
                NoteArtifactStoragePort.noop(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                null
        );
    }

    public MeetingIntelligenceHandoffAdapter(
            MeetingIntelligenceApi meetingIntelligenceApi,
            EvidenceValidationApi evidenceValidationApi,
            TranscriptSegmentSourcePort segmentSource,
            MeetingIntelligenceAuditPort auditPort,
            Optional<MeetingApi> meetingApi,
            NoteArtifactStoragePort noteArtifactStorage,
            Optional<PlatformUserNotificationPublisher> notificationPublisher,
            Optional<DeliveryApi> deliveryApi,
            Optional<DeliveryWorker> deliveryWorker
    ) {
        this(
                meetingIntelligenceApi,
                evidenceValidationApi,
                segmentSource,
                auditPort,
                meetingApi,
                noteArtifactStorage,
                notificationPublisher,
                deliveryApi,
                deliveryWorker,
                null
        );
    }

    public MeetingIntelligenceHandoffAdapter(
            MeetingIntelligenceApi meetingIntelligenceApi,
            EvidenceValidationApi evidenceValidationApi,
            TranscriptSegmentSourcePort segmentSource,
            MeetingIntelligenceAuditPort auditPort,
            Optional<MeetingApi> meetingApi,
            NoteArtifactStoragePort noteArtifactStorage,
            Optional<PlatformUserNotificationPublisher> notificationPublisher,
            Optional<DeliveryApi> deliveryApi,
            Optional<DeliveryWorker> deliveryWorker,
            String portalBaseUrl
    ) {
        this.meetingIntelligenceApi = Objects.requireNonNull(meetingIntelligenceApi, "meetingIntelligenceApi");
        this.evidenceValidationApi = Objects.requireNonNull(evidenceValidationApi, "evidenceValidationApi");
        this.segmentSource = Objects.requireNonNull(segmentSource, "segmentSource");
        this.auditPort = Objects.requireNonNull(auditPort, "auditPort");
        this.meetingApi = meetingApi == null ? Optional.empty() : meetingApi;
        this.noteArtifactStorage = Objects.requireNonNull(noteArtifactStorage, "noteArtifactStorage");
        this.notificationPublisher = notificationPublisher == null ? Optional.empty() : notificationPublisher;
        this.deliveryApi = deliveryApi == null ? Optional.empty() : deliveryApi;
        this.deliveryWorker = deliveryWorker == null ? Optional.empty() : deliveryWorker;
        this.portalBaseUrl = portalBaseUrl == null || portalBaseUrl.isBlank()
                ? "https://portal.nanobase.ai/easymeeting"
                : portalBaseUrl.trim();
    }

    @Override
    public Optional<UUID> handoff(HandoffCommand command) {
        Objects.requireNonNull(command, "command");

        ValidationExecutionResult validation = runValidation(command);
        QualityGateOutcome outcome = validation.decision().outcome();
        auditGate(command, validation);
        // Plan: LLM draft is visible before human approval. Persist unless hard-rejected.
        if (outcome == QualityGateOutcome.REJECTED) {
            return Optional.empty();
        }

        MeetingNoteDetailResponse note = meetingIntelligenceApi.mapAiCandidates(new MapAiCandidatesCommand(
                command.tenantId(),
                command.meetingOccurrenceId(),
                FinalNoteDraftMapper.toBundle(command.draft()),
                command.modelId(),
                command.promptVersionId(),
                command.schemaId(),
                clamp(command.draft().confidence())
        ));
        noteArtifactStorage.storeExtractionBundle(
                TenantId.of(command.tenantId()),
                command.meetingOccurrenceId(),
                command.jobId(),
                "{\"noteId\":\"" + note.id() + "\",\"executiveSummary\":"
                        + jsonString(command.draft().executiveSummary())
                        + ",\"qualityGateOutcome\":\"" + outcome.name() + "\"}"
        );
        auditPort.record(
                command.tenantId(),
                "system:ai-handoff",
                "MEETING_NOTE_MAPPED_FROM_AI",
                "MeetingNote",
                note.id(),
                Map.of(
                        "jobId", command.jobId().toString(),
                        "meetingOccurrenceId", command.meetingOccurrenceId().toString(),
                        "promptVersionId", command.promptVersionId(),
                        "modelId", command.modelId(),
                        "decisionCount", note.decisions().size(),
                        "actionItemCount", note.actionItems().size(),
                        "qualityGateOutcome", outcome.name()
                ),
                Instant.now()
        );
        notifyDraftMail(command, note);
        notifyDraftInApp(command, note);
        return Optional.of(note.id());
    }

    private void notifyDraftInApp(HandoffCommand command, MeetingNoteDetailResponse note) {
        if (notificationPublisher.isEmpty()) {
            return;
        }
        try {
            String title = null;
            if (meetingApi.isPresent()) {
                title = meetingApi.get().getMeeting(command.meetingOccurrenceId()).title();
            }
            notificationPublisher.get().notifyDraftMinutesReady(
                    command.tenantId(),
                    command.meetingOccurrenceId(),
                    note.id(),
                    title
            );
        } catch (RuntimeException ignored) {
            // In-app notification must not fail handoff.
        }
    }

    private void notifyDraftMail(HandoffCommand command, MeetingNoteDetailResponse note) {
        if (meetingApi.isEmpty()) {
            return;
        }
        if (deliveryApi.isEmpty()) {
            log.warn(
                    "Draft organizer mail skipped: DeliveryApi not available meetingId={} noteId={}",
                    command.meetingOccurrenceId(),
                    note.id()
            );
            return;
        }
        try {
            var meeting = meetingApi.get().getMeeting(command.meetingOccurrenceId());
            var participants = meetingApi.get().listParticipants(command.meetingOccurrenceId());
            String organizerEmail = participants.stream()
                    .filter(p -> p.participantType() != null && "ORGANIZER".equalsIgnoreCase(p.participantType().name()))
                    .map(p -> p.email())
                    .filter(email -> email != null && !email.isBlank())
                    .findFirst()
                    .orElseGet(() -> participants.stream()
                            .filter(p -> meeting.organizerUserId() != null
                                    && p.entraUserId() != null
                                    && meeting.organizerUserId().toString().equalsIgnoreCase(p.entraUserId()))
                            .map(p -> p.email())
                            .filter(email -> email != null && !email.isBlank())
                            .findFirst()
                            .orElseGet(() -> participants.stream()
                                    .map(p -> p.email())
                                    .filter(email -> email != null && !email.isBlank())
                                    .findFirst()
                                    .orElse(null)));
            if (organizerEmail == null || organizerEmail.isBlank()) {
                return;
            }
            String summary = note.currentVersion() == null ? "" : note.currentVersion().executiveSummary();
            UUID noteVersionId = note.currentVersion() == null ? note.id() : note.currentVersion().id();
            String title = meeting.title() == null || meeting.title().isBlank()
                    ? "Toplantı"
                    : meeting.title();
            String meetingUrl = DraftMinutesReadyMailBody.meetingDetailUrl(
                    portalBaseUrl, command.meetingOccurrenceId());
            DraftMinutesReadyMailBody body = new DraftMinutesReadyMailBody(
                    title,
                    formatWhen(meeting),
                    meetingUrl,
                    command.meetingOccurrenceId(),
                    summary == null ? "" : summary
            );
            deliveryApi.get().enqueueDraftOrganizerNotification(
                    command.tenantId(),
                    noteVersionId,
                    organizerEmail,
                    organizerEmail,
                    "Tutanak hazır · Onayınızı bekliyor — " + title,
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
            // Mail must not fail handoff.
            log.warn(
                    "Draft organizer Delivery enqueue failed meetingId={} noteId={}: {}",
                    command.meetingOccurrenceId(),
                    note.id(),
                    ex.toString()
            );
        }
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

    private ValidationExecutionResult runValidation(HandoffCommand command) {
        List<SegmentInput> segments = segmentSource.segmentsFor(
                TenantId.of(command.tenantId()), command.transcriptId());
        List<ValidationParticipant> speakers = ValidationCandidateMapper.participantsFromSpeakers(segments);
        List<ValidationParticipant> invitees = List.of();
        if (meetingApi.isPresent()) {
            invitees = meetingApi.get().listParticipants(command.meetingOccurrenceId()).stream()
                    .map(p -> ValidationCandidateMapper.fromInvitee(
                            p.displayName(), p.email(), p.entraUserId()))
                    .toList();
        }
        return evidenceValidationApi.validate(new RunValidationCommand(
                command.tenantId(),
                command.meetingOccurrenceId(),
                command.jobId(),
                ValidationCandidateMapper.toCandidates(
                        command.draft(),
                        command.meetingStartedAtIso(),
                        command.meetingTimezone()),
                ValidationCandidateMapper.toSegments(segments),
                ValidationCandidateMapper.mergeParticipants(speakers, invitees)
        ));
    }

    private void auditGate(HandoffCommand command, ValidationExecutionResult validation) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("jobId", command.jobId().toString());
        metadata.put("validationRunId", validation.run().id().toString());
        metadata.put("decisionId", validation.decision().id().toString());
        metadata.put("outcome", validation.decision().outcome().name());
        metadata.put("manualReview", validation.manualReviewCase().isPresent());
        auditPort.record(
                command.tenantId(),
                "system:ai-handoff",
                "QUALITY_GATE_" + validation.decision().outcome().name(),
                "QualityGateDecision",
                validation.decision().id(),
                metadata,
                Instant.now()
        );
    }

    private static double clamp(double confidence) {
        if (confidence < 0.0d) {
            return 0.0d;
        }
        if (confidence > 1.0d) {
            return 1.0d;
        }
        return confidence;
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
