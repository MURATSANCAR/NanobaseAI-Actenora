package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.aiprocessing.application.port.MeetingNoteHandoffPort;
import com.nanobaseai.actenora.aiprocessing.application.port.TranscriptSegmentSourcePort;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.delivery.api.DeliveryApi;
import com.nanobaseai.actenora.delivery.application.model.DraftMinutesReadyMailBody;
import com.nanobaseai.actenora.delivery.application.worker.DeliveryWorker;
import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.meetingintelligence.api.EvidenceValidationApi;
import com.nanobaseai.actenora.meetingintelligence.api.MeetingIntelligenceApi;
import com.nanobaseai.actenora.meetingintelligence.api.RunValidationCommand;
import com.nanobaseai.actenora.meetingintelligence.api.ValidationExecutionResult;
import com.nanobaseai.actenora.meetingintelligence.api.dto.ActionItemResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MapAiCandidatesCommand;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteDetailResponse;
import com.nanobaseai.actenora.meetingintelligence.application.MeetingNoteApprovalService;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingIntelligenceAuditPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.NoteArtifactStoragePort;
import com.nanobaseai.actenora.meetingintelligence.domain.model.NoteReviewStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.QualityGateOutcome;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationParticipant;
import com.nanobaseai.actenora.security.notification.PlatformUserNotificationPublisher;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
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

    private static final Duration APPROVAL_TTL = Duration.ofDays(7);

    private final MeetingIntelligenceApi meetingIntelligenceApi;
    private final EvidenceValidationApi evidenceValidationApi;
    private final TranscriptSegmentSourcePort segmentSource;
    private final MeetingIntelligenceAuditPort auditPort;
    private final Optional<MeetingApi> meetingApi;
    private final NoteArtifactStoragePort noteArtifactStorage;
    private final Optional<PlatformUserNotificationPublisher> notificationPublisher;
    private final Optional<DeliveryApi> deliveryApi;
    private final Optional<DeliveryWorker> deliveryWorker;
    private final java.util.function.Supplier<MeetingNoteApprovalService> noteApprovalService;
    private final String portalBaseUrl;
    private final List<String> additionalDraftRecipients;

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Istanbul");

    private static final DateTimeFormatter WHEN_FMT = DateTimeFormatter
            .ofPattern("d MMMM yyyy · HH:mm", Locale.forLanguageTag("tr"))
            .withZone(BUSINESS_ZONE);

    private static final DateTimeFormatter DUE_DATE_FMT = DateTimeFormatter
            .ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr"));

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
                () -> null,
                null,
                List.of()
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
                () -> null,
                null,
                List.of()
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
                () -> null,
                portalBaseUrl,
                List.of()
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
            java.util.function.Supplier<MeetingNoteApprovalService> noteApprovalService,
            String portalBaseUrl
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
                noteApprovalService,
                portalBaseUrl,
                List.of()
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
            java.util.function.Supplier<MeetingNoteApprovalService> noteApprovalService,
            String portalBaseUrl,
            List<String> additionalDraftRecipients
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
        this.noteApprovalService = noteApprovalService == null ? () -> null : noteApprovalService;
        this.portalBaseUrl = portalBaseUrl == null || portalBaseUrl.isBlank()
                ? "https://portal.nanobase.ai/easymeeting"
                : portalBaseUrl.trim();
        this.additionalDraftRecipients = normalizeEmails(additionalDraftRecipients);
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
        openApprovalIfActive(command, note);
        return Optional.of(note.id());
    }

    /**
     * ADR-010: ACTIVE notes must enter the human approval workflow so external delivery
     * stays gated on ApprovalGranted. MANUAL_REVIEW notes are left for human triage first.
     * Failure to open approval must not roll back the already-persisted note.
     */
    private void openApprovalIfActive(HandoffCommand command, MeetingNoteDetailResponse note) {
        MeetingNoteApprovalService approvalService = noteApprovalService.get();
        if (approvalService == null) {
            log.warn("MeetingNoteApprovalService not available; skip auto-approval open for note {}", note.id());
            return;
        }
        if (note.reviewStatus() != NoteReviewStatus.ACTIVE) {
            log.info(
                    "Skip auto-approval open: note {} reviewStatus={}",
                    note.id(), note.reviewStatus()
            );
            return;
        }
        String approverId = resolveApproverId(command);
        Instant expiresAt = Instant.now().plus(APPROVAL_TTL);
        try {
            ApprovalId approvalId = approvalService.submitForApproval(
                    command.tenantId(),
                    note.id(),
                    approverId,
                    expiresAt,
                    note.version()
            );
            log.info(
                    "Opened approval {} for note {} (approver={}, expiresAt={})",
                    approvalId.value(), note.id(), approverId, expiresAt
            );
            auditPort.record(
                    command.tenantId(),
                    "system:ai-handoff",
                    "NOTE_AUTO_SUBMITTED_FOR_APPROVAL",
                    "MeetingNote",
                    note.id(),
                    Map.of(
                            "approvalId", approvalId.value().toString(),
                            "approverId", approverId,
                            "meetingOccurrenceId", command.meetingOccurrenceId().toString()
                    ),
                    Instant.now()
            );
        } catch (RuntimeException ex) {
            log.warn(
                    "Failed to open approval for note {} — note remains ACTIVE without PENDING approval: {}",
                    note.id(), ex.toString()
            );
        }
    }

    private String resolveApproverId(HandoffCommand command) {
        if (meetingApi.isPresent()) {
            try {
                MeetingResponse meeting = meetingApi.get().getMeeting(command.meetingOccurrenceId());
                if (meeting != null && meeting.organizerUserId() != null) {
                    return meeting.organizerUserId().toString();
                }
            } catch (RuntimeException ignored) {
                // Fall through to system actor.
            }
        }
        return "system:organizer";
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
            List<String> recipients = draftMailRecipients(organizerEmail);
            if (recipients.isEmpty()) {
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
                    summary == null ? "" : summary,
                    draftDecisions(note),
                    draftActions(note),
                    draftOpenQuestions(note),
                    manualReviewCount(note)
            );
            String subject = "Tutanak hazır · Onayınızı bekliyor — " + title;
            String encoded = body.encode();
            for (String recipient : recipients) {
                deliveryApi.get().enqueueDraftOrganizerNotification(
                        command.tenantId(),
                        noteVersionId,
                        recipient,
                        recipient,
                        subject,
                        encoded
                );
            }
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

    /** Active decisions only — superseded ones must not resurface in the approval mail. */
    private static List<String> draftDecisions(MeetingNoteDetailResponse note) {
        if (note.decisions() == null) {
            return List.of();
        }
        return note.decisions().stream()
                .filter(d -> d.supersededByDecisionId() == null)
                .map(d -> d.text())
                .filter(text -> text != null && !text.isBlank())
                .toList();
    }

    private static List<DraftMinutesReadyMailBody.ActionLine> draftActions(MeetingNoteDetailResponse note) {
        if (note.actionItems() == null) {
            return List.of();
        }
        return note.actionItems().stream()
                .filter(a -> a.text() != null && !a.text().isBlank())
                .map(a -> new DraftMinutesReadyMailBody.ActionLine(
                        a.text(),
                        a.owner(),
                        actionDueLabel(a)))
                .toList();
    }

    private static String actionDueLabel(ActionItemResponse action) {
        if (action.dueDate() != null) {
            return DUE_DATE_FMT.format(action.dueDate());
        }
        if (action.dueAt() != null) {
            return DUE_DATE_FMT.format(action.dueAt().atZone(BUSINESS_ZONE).toLocalDate());
        }
        return action.relativeDate() == null ? "" : action.relativeDate();
    }

    private static List<String> draftOpenQuestions(MeetingNoteDetailResponse note) {
        if (note.openQuestions() == null) {
            return List.of();
        }
        return note.openQuestions().stream()
                .map(q -> q.text())
                .filter(text -> text != null && !text.isBlank())
                .toList();
    }

    /** Items the pipeline flagged for human verification before approval. */
    private static int manualReviewCount(MeetingNoteDetailResponse note) {
        int count = 0;
        if (note.decisions() != null) {
            count += note.decisions().stream().filter(d -> d.requiresManualReview()).count();
        }
        if (note.actionItems() != null) {
            count += note.actionItems().stream().filter(a -> a.requiresManualReview()).count();
        }
        if (note.risks() != null) {
            count += note.risks().stream().filter(r -> r.requiresManualReview()).count();
        }
        if (note.openQuestions() != null) {
            count += note.openQuestions().stream().filter(q -> q.requiresManualReview()).count();
        }
        return count;
    }

    /** Organizer (when known) plus configured extras; case-insensitive de-dupe. */
    List<String> draftMailRecipients(String organizerEmail) {
        return mergeDraftRecipients(organizerEmail, additionalDraftRecipients);
    }

    static List<String> mergeDraftRecipients(String organizerEmail, List<String> additional) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> emails = new ArrayList<>();
        addUniqueEmail(emails, seen, organizerEmail);
        if (additional != null) {
            for (String extra : additional) {
                addUniqueEmail(emails, seen, extra);
            }
        }
        return List.copyOf(emails);
    }

    private static void addUniqueEmail(List<String> emails, LinkedHashSet<String> seen, String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String trimmed = email.trim();
        if (seen.add(trimmed.toLowerCase(Locale.ROOT))) {
            emails.add(trimmed);
        }
    }

    static List<String> normalizeEmails(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> emails = new ArrayList<>();
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            for (String part : entry.split(",")) {
                addUniqueEmail(emails, seen, part);
            }
        }
        return List.copyOf(emails);
    }

    static List<String> parseAdditionalRecipients(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return normalizeEmails(List.of(csv));
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
