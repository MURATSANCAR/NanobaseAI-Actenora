package com.nanobaseai.actenora.security.portal;

import com.nanobaseai.actenora.approval.api.ApprovalApi;
import com.nanobaseai.actenora.approval.api.ApprovalDecisionType;
import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.approval.api.ApprovalRequestStatus;
import com.nanobaseai.actenora.approval.api.ApprovalRequestView;
import com.nanobaseai.actenora.aiprocessing.api.AiProcessingApi;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.audit.api.AuditApi;
import com.nanobaseai.actenora.delivery.api.DeliveryApi;
import com.nanobaseai.actenora.delivery.api.DeliveryRequestStatusView;
import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.api.RequiresPermission;
import com.nanobaseai.actenora.identity.api.UserView;
import com.nanobaseai.actenora.identity.api.Permission;
import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.api.dto.BusinessContextResponse;
import com.nanobaseai.actenora.meeting.api.dto.CursorPageRequest;
import com.nanobaseai.actenora.meeting.api.dto.MeetingListResponse;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.meeting.api.dto.ParticipantResponse;
import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrenceStatus;
import com.nanobaseai.actenora.meetingintelligence.api.ledger.ContinuityLedgerApi;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.CommitmentConfirmation;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.event.LedgerEvent;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.event.LedgerEventType;
import com.nanobaseai.actenora.meetingintelligence.application.MeetingNoteApprovalService;
import com.nanobaseai.actenora.meetingintelligence.api.MeetingIntelligenceApi;
import com.nanobaseai.actenora.meetingintelligence.api.dto.ActionItemResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.ActionItemUpdateRequest;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteDetailResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteUpdateRequest;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNote;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ActionItemStatus;
import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.microsoftconnection.application.model.GraphSubscription;
import com.nanobaseai.actenora.modelmanagement.api.ModelManagementApi;
import com.nanobaseai.actenora.modelmanagement.application.ActorPrincipal;
import com.nanobaseai.actenora.modelmanagement.application.ModelControlPermission;
import com.nanobaseai.actenora.modelmanagement.domain.DeploymentStatus;
import com.nanobaseai.actenora.operations.api.OperationsApi;
import com.nanobaseai.actenora.operations.application.OperationsViews;
import com.nanobaseai.actenora.security.aiprocessing.NanobaseAiBrandSanitizer;
import com.nanobaseai.actenora.security.aiprocessing.NanobaseAiConnectionService;
import com.nanobaseai.actenora.security.microsoftconnection.GraphObservability;
import com.nanobaseai.actenora.security.microsoftconnection.TeamsTranscriptPollScheduler;
import com.nanobaseai.actenora.notification.api.NotificationApi;
import com.nanobaseai.actenora.notification.api.UserNotificationListView;
import com.nanobaseai.actenora.notification.api.UserNotificationView;
import com.nanobaseai.actenora.security.notification.PlatformUserNotificationPublisher;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectMetadata;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorage;
import com.nanobaseai.actenora.template.api.MeetingTemplateId;
import com.nanobaseai.actenora.template.api.RenderJobView;
import com.nanobaseai.actenora.template.api.RenderedDocumentView;
import com.nanobaseai.actenora.template.api.TemplateApi;
import com.nanobaseai.actenora.template.api.TemplateVersionId;
import com.nanobaseai.actenora.template.domain.DesignComponent;
import com.nanobaseai.actenora.template.domain.DesignSchema;
import com.nanobaseai.actenora.template.domain.MeetingTemplate;
import com.nanobaseai.actenora.template.domain.TemplateVersion;
import com.nanobaseai.actenora.template.domain.TemplateVersionStatus;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import com.nanobaseai.actenora.transcript.api.TranscriptApi;
import com.nanobaseai.actenora.transcript.api.dto.TranscriptSegmentView;
import com.nanobaseai.actenora.transcript.domain.TranscriptDomainException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * FAZ 33 — portal BFF: web-portal OpenAPI shapes composed from domain façades.
 * Mounted under {@code /api/v1/portal} to avoid clashing with domain controllers
 * ({@code /api/v1/me} → UserView, {@code /api/v1/meetings} → MeetingResponse).
 */
@RestController
@RequestMapping("/api/v1/portal")
public class PortalApiController {

    public static final String COMPOSITION_STUB_HEADER = "X-Actenora-Composition";

    private final IdentityApi identityApi;
    private final MeetingApi meetingApi;
    private final ContinuityLedgerApi ledgerApi;
    private final ApprovalApi approvalApi;
    private final MeetingNoteApprovalService noteApprovalService;
    private final Optional<MeetingIntelligenceApi> meetingIntelligenceApi;
    private final Optional<OperationsApi> operationsApi;
    private final Optional<TranscriptApi> transcriptApi;
    private final Optional<TemplateApi> templateApi;
    private final Optional<MicrosoftConnectionApi> microsoftConnectionApi;
    private final Optional<ModelManagementApi> modelManagementApi;
    private final PortalTeamsPreferencesStore teamsPreferencesStore;
    private final Optional<NanobaseAiConnectionService> nanobaseAiConnectionService;
    private final Optional<AiProcessingApi> aiProcessingApi;
    private final Optional<AuditApi> auditApi;
    private final String graphClientId;
    private final Optional<GraphObservability> graphObservability;
    private final Optional<TeamsTranscriptPollScheduler> transcriptPollScheduler;
    private final Optional<NotificationApi> notificationApi;
    private final Optional<PlatformUserNotificationPublisher> notificationPublisher;
    private final Optional<DeliveryApi> deliveryApi;
    private final Optional<ObjectStorage> objectStorage;

    public PortalApiController(
            IdentityApi identityApi,
            MeetingApi meetingApi,
            ContinuityLedgerApi ledgerApi,
            ApprovalApi approvalApi,
            MeetingNoteApprovalService noteApprovalService,
            ObjectProvider<MeetingIntelligenceApi> meetingIntelligenceApi,
            ObjectProvider<OperationsApi> operationsApi,
            ObjectProvider<TranscriptApi> transcriptApi,
            ObjectProvider<TemplateApi> templateApi,
            ObjectProvider<MicrosoftConnectionApi> microsoftConnectionApi,
            ObjectProvider<ModelManagementApi> modelManagementApi,
            ObjectProvider<NanobaseAiConnectionService> nanobaseAiConnectionService,
            ObjectProvider<AiProcessingApi> aiProcessingApi,
            ObjectProvider<AuditApi> auditApi,
            ObjectProvider<GraphObservability> graphObservability,
            ObjectProvider<TeamsTranscriptPollScheduler> transcriptPollScheduler,
            ObjectProvider<NotificationApi> notificationApi,
            ObjectProvider<PlatformUserNotificationPublisher> notificationPublisher,
            ObjectProvider<DeliveryApi> deliveryApi,
            ObjectProvider<ObjectStorage> objectStorage,
            PortalTeamsPreferencesStore teamsPreferencesStore,
            @Value("${actenora.microsoft-graph.client-id:}") String graphClientId
    ) {
        this.identityApi = Objects.requireNonNull(identityApi, "identityApi");
        this.meetingApi = Objects.requireNonNull(meetingApi, "meetingApi");
        this.ledgerApi = Objects.requireNonNull(ledgerApi, "ledgerApi");
        this.approvalApi = Objects.requireNonNull(approvalApi, "approvalApi");
        this.noteApprovalService = Objects.requireNonNull(noteApprovalService, "noteApprovalService");
        this.meetingIntelligenceApi = Optional.ofNullable(meetingIntelligenceApi.getIfAvailable());
        this.operationsApi = Optional.ofNullable(operationsApi.getIfAvailable());
        this.transcriptApi = Optional.ofNullable(transcriptApi.getIfAvailable());
        this.templateApi = Optional.ofNullable(templateApi.getIfAvailable());
        this.microsoftConnectionApi = Optional.ofNullable(microsoftConnectionApi.getIfAvailable());
        this.modelManagementApi = Optional.ofNullable(modelManagementApi.getIfAvailable());
        this.nanobaseAiConnectionService = Optional.ofNullable(nanobaseAiConnectionService.getIfAvailable());
        this.aiProcessingApi = Optional.ofNullable(aiProcessingApi.getIfAvailable());
        this.auditApi = Optional.ofNullable(auditApi.getIfAvailable());
        this.graphObservability = Optional.ofNullable(graphObservability.getIfAvailable());
        this.transcriptPollScheduler = Optional.ofNullable(transcriptPollScheduler.getIfAvailable());
        this.notificationApi = Optional.ofNullable(notificationApi.getIfAvailable());
        this.notificationPublisher = Optional.ofNullable(notificationPublisher.getIfAvailable());
        this.deliveryApi = Optional.ofNullable(deliveryApi.getIfAvailable());
        this.objectStorage = Optional.ofNullable(objectStorage.getIfAvailable());
        this.teamsPreferencesStore = Objects.requireNonNull(teamsPreferencesStore, "teamsPreferencesStore");
        this.graphClientId = graphClientId == null ? "" : graphClientId;
    }

    @GetMapping("/me")
    public PortalUserView me() {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        UserView user = identityApi.currentUser(principal);
        return new PortalUserView(
                user.id(),
                user.displayName(),
                user.email(),
                PortalPermissionMapper.toPortalRole(
                        user.roles().stream().map(Enum::name).collect(Collectors.toSet())
                ),
                user.tenantId().value(),
                PortalPermissionMapper.toPortalPermissions(user.permissions())
        );
    }

    @GetMapping("/dashboard")
    @RequiresPermission(Permission.MEETING_READ)
    public DashboardView dashboard() {
        AuthenticatedPrincipal principal = require(Permission.MEETING_READ);
        List<MeetingSummaryView> recent = toSummaries(
                meetingApi.listMeetings(new CursorPageRequest(null, null, null, 50)).items()
        );
        int pendingApprovals = countPendingApprovals(principal.tenantId().value());
        int openActions = ledgerApi.listOpenActionItems(principal.tenantId()).size();
        int overdue = ledgerApi.overdueCommitments(principal.tenantId()).size();
        int runningJobs = resolveRunningJobs(principal.tenantId().value());
        return new DashboardView(pendingApprovals, openActions, overdue, runningJobs, recent);
    }

    @GetMapping("/notifications")
    @RequiresPermission(Permission.MEETING_READ)
    public PortalNotificationFeedView listNotifications(
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        AuthenticatedPrincipal principal = require(Permission.MEETING_READ);
        if (notificationApi.isEmpty()) {
            return new PortalNotificationFeedView(List.of(), 0);
        }
        notificationPublisher.ifPresent(p -> p.ensureOverdueNotifications(principal.tenantId()));
        String oid = recipientOid(principal);
        UserNotificationListView list = notificationApi.get().listForRecipient(
                principal.tenantId(),
                oid,
                limit == null ? 30 : limit
        );
        return new PortalNotificationFeedView(
                list.items().stream().map(PortalApiController::toNotificationItem).toList(),
                list.unreadCount()
        );
    }

    @PostMapping("/notifications/{id}/read")
    @RequiresPermission(Permission.MEETING_READ)
    public ResponseEntity<Void> markNotificationRead(@PathVariable UUID id) {
        AuthenticatedPrincipal principal = require(Permission.MEETING_READ);
        if (notificationApi.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        boolean ok = notificationApi.get().markRead(principal.tenantId(), recipientOid(principal), id);
        if (!ok) {
            throw new ActenoraException("NOTIFICATION_NOT_FOUND", "Notification not found: " + id);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/notifications/read-all")
    @RequiresPermission(Permission.MEETING_READ)
    public ResponseEntity<Void> markAllNotificationsRead() {
        AuthenticatedPrincipal principal = require(Permission.MEETING_READ);
        if (notificationApi.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        notificationApi.get().markAllRead(principal.tenantId(), recipientOid(principal));
        return ResponseEntity.noContent().build();
    }

    private static String recipientOid(AuthenticatedPrincipal principal) {
        if (principal.entraObjectId() != null && !principal.entraObjectId().isBlank()) {
            return principal.entraObjectId();
        }
        return principal.userId().toString();
    }

    private static PortalNotificationItemView toNotificationItem(UserNotificationView view) {
        return new PortalNotificationItemView(
                view.id(),
                view.type().name(),
                view.title(),
                view.body(),
                view.href(),
                view.createdAt() == null ? null : view.createdAt().toString(),
                view.readAt() == null ? null : view.readAt().toString()
        );
    }

    @GetMapping("/meetings")
    @RequiresPermission(Permission.MEETING_READ)
    public PortalCursorPage<MeetingSummaryView> listMeetings(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "q", required = false) String q
    ) {
        require(Permission.MEETING_READ);
        MeetingOccurrenceStatus parsed = parseStatus(status);
        if (q != null && !q.isBlank()) {
            int searchLimit = limit == null || limit < 1 ? 50 : Math.min(limit, 200);
            return new PortalCursorPage<>(
                    toSummaries(meetingApi.searchMeetings(q.trim(), parsed, searchLimit)),
                    null);
        }
        MeetingListResponse page = meetingApi.listMeetings(
                new CursorPageRequest(parsed, null, cursor, limit == null ? 50 : limit)
        );
        return new PortalCursorPage<>(toSummaries(page.items()), page.nextCursor());
    }

    @GetMapping("/meetings/{meetingId}")
    @RequiresPermission(Permission.MEETING_READ)
    public MeetingDetailView meetingDetail(@PathVariable UUID meetingId) {
        require(Permission.MEETING_READ);
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        MeetingResponse meeting = meetingApi.getMeeting(meetingId);
        List<ParticipantResponse> participants = meetingApi.listParticipants(meetingId);
        MeetingSummaryView summary = toSummary(meeting, participants.size());

        List<PortalParticipantView> portalParticipants = participants.stream()
                .map(p -> new PortalParticipantView(
                        p.id(),
                        p.displayName(),
                        p.email() == null ? "" : p.email(),
                        p.participantType() == null ? "UNKNOWN" : p.participantType().name(),
                        p.attendanceStatus() == null ? "UNKNOWN" : p.attendanceStatus().name(),
                        p.external()
                ))
                .toList();

        List<DecisionItemView> decisions = new ArrayList<>();
        List<CommitmentItemView> commitments = new ArrayList<>();
        List<ActionItemView> actions = new ArrayList<>();
        List<RiskItemView> risks = new ArrayList<>();
        List<MeetingNoteView> notes = new ArrayList<>();
        List<MeetingVersionView> versions = new ArrayList<>();
        List<String> qualityFlags = new ArrayList<>();
        List<ApprovalRecordView> approvalHistory = new ArrayList<>();
        java.util.Set<UUID> seenDecisionIds = new java.util.LinkedHashSet<>();
        java.util.Set<UUID> seenCommitmentIds = new java.util.LinkedHashSet<>();
        java.util.Set<UUID> seenActionIds = new java.util.LinkedHashSet<>();
        Map<String, TranscriptSegmentView> segmentsById = loadTranscriptSegmentsById(meetingId);

        if (meetingIntelligenceApi.isPresent()) {
            for (MeetingNoteDetailResponse note : meetingIntelligenceApi.get().listNotesForMeeting(meetingId)) {
                String approvalStatus = note.currentVersion() == null || note.currentVersion().approvalStatus() == null
                        ? "DRAFT"
                        : note.currentVersion().approvalStatus().name();
                String body = renderDraftMinutesBody(note, meeting.title());
                notes.add(new MeetingNoteView(
                        note.id(),
                        "SHARED",
                        body,
                        note.updatedAt() == null ? null : note.updatedAt().toString(),
                        note.currentVersion() == null ? null : note.currentVersion().createdByUserId(),
                        approvalStatus,
                        "DRAFT".equals(approvalStatus),
                        note.version()
                ));
                if (note.currentVersion() != null) {
                    versions.add(new MeetingVersionView(
                            note.currentVersion().versionNumber(),
                            "DRAFT".equals(approvalStatus)
                                    ? NanobaseAiBrandSanitizer.draftStatusLabel()
                                    : "v" + note.currentVersion().versionNumber(),
                            note.currentVersion().createdAt() == null
                                    ? null
                                    : note.currentVersion().createdAt().toString()
                    ));
                    approvalApi.findBySubject(
                            principal.tenantId().value(),
                            note.currentVersion().id()
                    ).ifPresent(approvalId ->
                            approvalApi.get(principal.tenantId().value(), approvalId).ifPresent(view ->
                                    approvalHistory.add(toApprovalRecord(view, note.id(), null))));
                }
                if (note.qualityFlags() != null) {
                    java.util.LinkedHashSet<String> uniqueFlags = new java.util.LinkedHashSet<>();
                    for (var flag : note.qualityFlags()) {
                        if (flag == null || flag.code() == null) {
                            continue;
                        }
                        String label = portalQualityFlagLabel(flag.code().name(), flag.detail());
                        if (label != null) {
                            uniqueFlags.add(label);
                        }
                    }
                    qualityFlags.addAll(uniqueFlags);
                }
                Map<UUID, List<PortalEvidenceView>> evidenceBySubject = indexEvidence(note, segmentsById);
                for (var decision : note.decisions()) {
                    if (decision == null || !seenDecisionIds.add(decision.id())) {
                        continue;
                    }
                    decisions.add(new DecisionItemView(
                            decision.id(),
                            meetingId,
                            decision.text(),
                            decisionStatus(decision),
                            evidenceBySubject.getOrDefault(decision.id(), List.of()),
                            decision.updatedAt() == null ? null : decision.updatedAt().toString(),
                            blankToNull(decision.rationale()),
                            blankToNull(decision.decisionStatus())
                    ));
                }
                for (var action : note.actionItems()) {
                    if (action == null || !seenActionIds.add(action.id())) {
                        continue;
                    }
                    String dueAtDisplay = null;
                    if (action.dueAt() != null) {
                        dueAtDisplay = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                                .withZone(java.time.ZoneId.of("Europe/Istanbul"))
                                .format(action.dueAt());
                    } else if (action.dueDate() != null) {
                        dueAtDisplay = action.dueDate().toString();
                    }
                    actions.add(new ActionItemView(
                            action.id(),
                            meetingId,
                            action.text(),
                            action.status() == null ? "OPEN" : action.status().name(),
                            action.owner() == null || action.owner().isBlank() ? "—" : action.owner(),
                            dueAtDisplay,
                            evidenceBySubject.getOrDefault(action.id(), List.of()),
                            blankToNull(action.ownerType()),
                            blankToNull(action.priority()),
                            blankToNull(action.relativeDate())
                    ));
                }
                for (var risk : note.risks()) {
                    if (risk == null) {
                        continue;
                    }
                    risks.add(new RiskItemView(
                            risk.id(),
                            risk.text(),
                            riskSeverity(risk.aiConfidence()),
                            evidenceBySubject.getOrDefault(risk.id(), List.of()),
                            blankToNull(risk.likelihood()),
                            blankToNull(risk.mitigation())
                    ));
                }
                for (var commitment : note.commitments()) {
                    if (commitment == null || !seenCommitmentIds.add(commitment.id())) {
                        continue;
                    }
                    commitments.add(new CommitmentItemView(
                            commitment.id(),
                            meetingId,
                            commitment.text(),
                            commitment.owner() == null || commitment.owner().isBlank() ? "—" : commitment.owner(),
                            null,
                            commitment.confirmationStatus() == null
                                    ? "OPEN"
                                    : commitment.confirmationStatus().name(),
                            evidenceBySubject.getOrDefault(commitment.id(), List.of())
                    ));
                }
            }
        }

        for (LedgerEvent event : ledgerApi.listEvents(principal.tenantId())) {
            if (!meetingId.equals(event.meetingOccurrenceId())) {
                continue;
            }
            if (event.type() == LedgerEventType.DECISION_RECORDED && seenDecisionIds.add(event.aggregateId())) {
                decisions.add(new DecisionItemView(
                        event.aggregateId(),
                        meetingId,
                        payloadText(event),
                        "APPROVED",
                        List.of(),
                        event.occurredAt().toString(),
                        null,
                        null
                ));
            } else if (event.type() == LedgerEventType.COMMITMENT_RECORDED
                    && seenCommitmentIds.add(event.aggregateId())) {
                commitments.add(new CommitmentItemView(
                        event.aggregateId(),
                        meetingId,
                        payloadText(event),
                        event.optional("owner") == null ? "unknown" : event.optional("owner"),
                        event.optional("dueDate"),
                        "OPEN",
                        List.of()
                ));
            }
        }

        for (ActionItemView ledgerAction : ledgerApi.listOpenTasks(principal.tenantId(), meetingId).stream()
                .map(PortalApiController::toActionItemView)
                .toList()) {
            if (seenActionIds.add(ledgerAction.id())) {
                actions.add(ledgerAction);
            }
        }

        return new MeetingDetailView(
                summary,
                portalParticipants,
                null,
                resolveBusinessContextName(meeting.businessContextId()),
                versions,
                approvalHistory,
                notes,
                decisions,
                actions,
                risks,
                commitments,
                List.copyOf(qualityFlags),
                false
        );
    }

    private TenantId principalTenantId() {
        return TenantSecurityContext.require().tenantId();
    }


    @GetMapping("/meetings/{meetingId}/delivery")
    @RequiresPermission(Permission.MEETING_READ)
    public List<PortalDeliveryRequestView> meetingDelivery(
            @PathVariable UUID meetingId,
            HttpServletResponse response
    ) {
        require(Permission.MEETING_READ);
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        meetingApi.getMeeting(meetingId);
        if (deliveryApi.isEmpty() || meetingIntelligenceApi.isEmpty()) {
            markStub(response);
            return List.of();
        }
        List<PortalDeliveryRequestView> out = new ArrayList<>();
        java.util.Set<UUID> seen = new java.util.LinkedHashSet<>();
        for (MeetingNoteDetailResponse note : meetingIntelligenceApi.get().listNotesForMeeting(meetingId)) {
            if (note.currentVersion() == null) {
                continue;
            }
            UUID noteVersionId = note.currentVersion().id();
            for (DeliveryRequestStatusView req : deliveryApi.get().listByNoteVersion(
                    principal.tenantId().value(), noteVersionId)) {
                if (req == null || !seen.add(req.id())) {
                    continue;
                }
                out.add(new PortalDeliveryRequestView(
                        req.id(),
                        req.noteVersionId(),
                        req.intent(),
                        req.status() == null ? null : req.status().name(),
                        req.recipientEmail(),
                        req.createdAt() == null ? null : req.createdAt().toString(),
                        req.updatedAt() == null ? null : req.updatedAt().toString()
                ));
            }
        }
        return out;
    }


    @GetMapping("/meetings/{meetingId}/notes/{noteId}/renders")
    @RequiresPermission(Permission.MEETING_READ)
    public NoteRenderStatusView noteRenders(
            @PathVariable UUID meetingId,
            @PathVariable UUID noteId,
            HttpServletResponse response
    ) {
        require(Permission.MEETING_READ);
        meetingApi.getMeeting(meetingId);
        requireNoteBelongsToMeeting(meetingId, noteId);
        TenantId tenantId = principalTenantId();
        String portalPdfPath = portalNotePdfPath(meetingId, noteId);

        List<PortalRenderJobView> jobs = List.of();
        List<PortalRenderedDocumentView> documents = new ArrayList<>();
        if (templateApi.isPresent()) {
            jobs = templateApi.get().listRenderJobsForNote(tenantId, noteId).stream()
                    .map(j -> new PortalRenderJobView(
                            j.id(),
                            j.format(),
                            j.status(),
                            j.renderedDocumentId().orElse(null),
                            j.createdAt() == null ? null : j.createdAt().toString(),
                            j.updatedAt() == null ? null : j.updatedAt().toString(),
                            j.lastError().orElse(null)
                    ))
                    .toList();
            for (RenderedDocumentView doc : templateApi.get().listRenderedDocumentsForNote(tenantId, noteId)) {
                // Browser cannot reach internal MinIO; expose same-origin portal PDF proxy.
                documents.add(new PortalRenderedDocumentView(
                        doc.id(),
                        doc.format(),
                        doc.sizeBytes(),
                        "PDF".equalsIgnoreCase(doc.format()) ? portalPdfPath : null,
                        null,
                        doc.createdAt() == null ? null : doc.createdAt().toString()
                ));
            }
        } else {
            markStub(response);
        }

        boolean hasPdf = documents.stream().anyMatch(d -> "PDF".equalsIgnoreCase(d.format()));
        if (!hasPdf) {
            resolveFallbackPdf(tenantId, noteId).ifPresent(fallback -> documents.add(
                    new PortalRenderedDocumentView(
                            fallback.documentId(),
                            "PDF",
                            fallback.sizeBytes(),
                            portalPdfPath,
                            null,
                            fallback.createdAt() == null ? null : fallback.createdAt().toString()
                    )
            ));
        }
        return new NoteRenderStatusView(jobs, documents);
    }

    /**
     * Streams the approved note PDF through the portal BFF so browsers do not need
     * direct MinIO access (prod-like MinIO is often not published on the host).
     */
    @GetMapping("/meetings/{meetingId}/notes/{noteId}/pdf")
    @RequiresPermission(Permission.MEETING_READ)
    public ResponseEntity<InputStreamResource> downloadNotePdf(
            @PathVariable UUID meetingId,
            @PathVariable UUID noteId
    ) {
        require(Permission.MEETING_READ);
        meetingApi.getMeeting(meetingId);
        requireNoteBelongsToMeeting(meetingId, noteId);
        if (objectStorage.isEmpty()) {
            throw new ActenoraException("PDF_STORAGE_UNAVAILABLE", "Object storage is not configured");
        }
        ResolvedNotePdf pdf = resolveNotePdf(principalTenantId(), noteId)
                .orElseThrow(() -> new ActenoraException("PDF_NOT_FOUND", "No rendered PDF for this note"));
        InputStream stream = objectStorage.get().get(pdf.storageKey());
        InputStreamResource body = new InputStreamResource(stream);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"meeting-minutes.pdf\"")
                .contentType(MediaType.APPLICATION_PDF);
        if (pdf.sizeBytes() > 0) {
            response = response.contentLength(pdf.sizeBytes());
        }
        return response.body(body);
    }

    @GetMapping("/meetings/{meetingId}/transcript")
    @RequiresPermission(Permission.MEETING_READ)
    public TranscriptView transcript(
            @PathVariable UUID meetingId,
            @RequestParam(value = "speaker", required = false) String speaker,
            @RequestParam(value = "q", required = false) String q,
            HttpServletResponse response
    ) {
        require(Permission.MEETING_READ);
        meetingApi.getMeeting(meetingId);
        List<String> participantRoster = meetingApi.listParticipants(meetingId).stream()
                .map(ParticipantResponse::displayName)
                .filter(Objects::nonNull)
                .toList();
        if (transcriptApi.isEmpty()) {
            markStub(response);
            return new TranscriptView(List.of(), List.of());
        }
        try {
            List<TranscriptSegmentView> segments =
                    transcriptApi.get().listSegmentsForMeeting(principalTenantId(), meetingId);
            if (speaker != null && !speaker.isBlank()) {
                String needle = speaker.trim().toLowerCase(Locale.ROOT);
                segments = segments.stream()
                        .filter(s -> s.speaker() != null
                                && s.speaker().toLowerCase(Locale.ROOT).contains(needle))
                        .toList();
            }
            if (q != null && !q.isBlank()) {
                String needle = q.trim().toLowerCase(Locale.ROOT);
                segments = segments.stream()
                        .filter(s -> s.text() != null
                                && s.text().toLowerCase(Locale.ROOT).contains(needle))
                        .toList();
            }
            List<String> speakers = transcriptApi.get().listSpeakersForMeeting(principalTenantId(), meetingId);
            List<Map<String, Object>> segmentPayload = segments.stream()
                    .map(segment -> toTranscriptSegment(segment, participantRoster))
                    .toList();
            return new TranscriptView(segmentPayload, speakers);
        } catch (TranscriptDomainException ex) {
            if ("TRANSCRIPT_NOT_FOUND".equals(ex.code())) {
                return new TranscriptView(List.of(), List.of());
            }
            throw ex;
        }
    }

    private static Map<String, Object> toTranscriptSegment(
            TranscriptSegmentView segment,
            List<String> participantRoster
    ) {
        SpeakerConfidenceAssessment.Result speaker =
                SpeakerConfidenceAssessment.assess(segment.speaker(), participantRoster);
        return Map.of(
                "id", segment.id(),
                "speaker", segment.speaker(),
                "text", segment.text(),
                "startMs", segment.startMs(),
                "endMs", segment.endMs(),
                "markers", segment.markers(),
                "speakerResolutionStatus", speaker.status(),
                "speakerConfidence", speaker.confidence(),
                "speakerReviewRequired", speaker.reviewRequired()
        );
    }

    @PutMapping("/meetings/{meetingId}/notes/{noteId}")
    @RequiresPermission(Permission.MEETING_WRITE)
    public MeetingNoteView updateNote(
            @PathVariable UUID meetingId,
            @PathVariable UUID noteId,
            @RequestBody UpdateNoteBody body
    ) {
        require(Permission.MEETING_WRITE);
        meetingApi.getMeeting(meetingId);
        if (meetingIntelligenceApi.isEmpty()) {
            throw new ActenoraException(
                    "NOTE_UPDATE_UNAVAILABLE",
                    "Meeting intelligence module is not available; note update is not wired"
            );
        }
        if (body == null || body.body() == null || body.body().isBlank()) {
            throw new ActenoraException("INVALID_NOTE_BODY", "note body is required");
        }
        var updated = meetingIntelligenceApi.get().updateNote(
                noteId,
                new MeetingNoteUpdateRequest(body.body(), null, 0L)
        );
        return new MeetingNoteView(
                updated.id(),
                "SHARED",
                body.body(),
                updated.updatedAt().toString(),
                TenantSecurityContext.require().userId(),
                updated.currentVersion() == null || updated.currentVersion().approvalStatus() == null
                        ? "DRAFT"
                        : updated.currentVersion().approvalStatus().name(),
                updated.currentVersion() == null
                        || updated.currentVersion().approvalStatus() == null
                        || "DRAFT".equals(updated.currentVersion().approvalStatus().name()),
                updated.version()
        );
    }

    @PostMapping("/meetings/{meetingId}/notes/{noteId}/submit-for-approval")
    @RequiresPermission(Permission.MEETING_WRITE)
    public ApprovalRecordView submitNoteForApproval(
            @PathVariable UUID meetingId,
            @PathVariable UUID noteId,
            @RequestBody(required = false) SubmitNoteApprovalBody body
    ) {
        AuthenticatedPrincipal principal = require(Permission.MEETING_WRITE);
        meetingApi.getMeeting(meetingId);
        if (meetingIntelligenceApi.isEmpty()) {
            throw new ActenoraException(
                    "NOTE_APPROVAL_UNAVAILABLE",
                    "Meeting intelligence module is not available; approval submit is not wired"
            );
        }
        var note = meetingIntelligenceApi.get().getNoteDetail(noteId);
        if (!meetingId.equals(note.meetingOccurrenceId())) {
            throw new ActenoraException("NOTE_MEETING_MISMATCH", "Note does not belong to this meeting");
        }
        long expectedVersion = body == null || body.expectedVersion() == null
                ? note.version()
                : body.expectedVersion();
        ApprovalId approvalId = noteApprovalService.submitForApproval(
                principal.tenantId().value(),
                noteId,
                principal.userId().toString(),
                null,
                expectedVersion
        );
        ApprovalRequestView view = approvalApi.get(principal.tenantId().value(), approvalId)
                .orElseThrow();
        return toApprovalRecord(view, noteId, principal.displayName());
    }

    /**
     * Decide a pending note approval.
     * <p>
     * Dedicated approvers use {@link Permission#APPROVAL_DECIDE}. Meeting editors
     * ({@link Permission#MEETING_WRITE}) may also decide when they are the assigned
     * required approver — portal submit assigns the submitter, so organizers can
     * self-approve the Teams meeting note they just sent for review. The Approval BC
     * still rejects actors who are not the required approver.
     */
    @PostMapping("/approvals/{approvalId}/decide")
    public ApprovalRecordView decideApproval(
            @PathVariable UUID approvalId,
            @RequestBody DecideBody body
    ) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        boolean canDecide = principal.hasPermission(Permission.APPROVAL_DECIDE.code());
        boolean canWrite = principal.hasPermission(Permission.MEETING_WRITE.code());
        if (!canDecide && !canWrite) {
            identityApi.requirePermission(principal, Permission.APPROVAL_DECIDE);
        }
        ApprovalDecisionType decisionType = parseDecision(body.decision());
        MeetingNote note = noteApprovalService.decideByApprovalId(
                principal.tenantId().value(),
                ApprovalId.of(approvalId),
                principal.userId().toString(),
                decisionType,
                body.comment(),
                body.expectedNoteVersion(),
                body.expectedApprovalVersion()
        );
        ApprovalRequestView view = approvalApi.get(principal.tenantId().value(), ApprovalId.of(approvalId))
                .orElseThrow();
        return toApprovalRecord(view, note.id(), principal.displayName());
    }

    @GetMapping("/approvals/pending")
    @RequiresPermission(Permission.APPROVAL_DECIDE)
    public PendingApprovalsInboxView listPendingApprovals() {
        require(Permission.APPROVAL_DECIDE);
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        UUID tenantId = principal.tenantId().value();
        Map<UUID, MutablePendingApprovalGroup> groups = new LinkedHashMap<>();
        for (ApprovalRequestView view : approvalApi.listForTenant(tenantId)) {
            if (!isPendingApproval(view)) {
                continue;
            }
            UUID meetingId = null;
            String meetingTitle = "(untitled)";
            UUID artifactId = view.subjectId();
            if (meetingIntelligenceApi.isPresent()) {
                Optional<MeetingNoteDetailResponse> note = meetingIntelligenceApi.get()
                        .findNoteByVersionId(view.subjectId());
                if (note.isPresent()) {
                    meetingId = note.get().meetingOccurrenceId();
                    artifactId = note.get().id();
                    try {
                        MeetingResponse meeting = meetingApi.getMeeting(meetingId);
                        meetingTitle = meeting.title() == null ? "(untitled)" : meeting.title();
                    } catch (RuntimeException ignored) {
                        /* keep default title */
                    }
                }
            }
            UUID groupKey = meetingId != null ? meetingId : artifactId;
            UUID groupMeetingId = meetingId != null ? meetingId : groupKey;
            String groupTitle = meetingTitle;
            UUID recordArtifactId = artifactId;
            groups.computeIfAbsent(groupKey, key -> new MutablePendingApprovalGroup(groupMeetingId, groupTitle))
                    .items.add(toPendingApprovalRecord(view, recordArtifactId));
        }
        List<PendingApprovalGroupView> out = groups.values().stream()
                .map(group -> new PendingApprovalGroupView(group.meetingId, group.meetingTitle, List.copyOf(group.items)))
                .toList();
        return new PendingApprovalsInboxView(out);
    }

    @GetMapping("/decisions")
    @RequiresPermission(Permission.MEETING_READ)
    public PortalCursorPage<DecisionItemView> listDecisions(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        AuthenticatedPrincipal principal = require(Permission.MEETING_READ);
        List<DecisionItemView> items = new ArrayList<>();
        for (LedgerEvent event : ledgerApi.listEvents(principal.tenantId())) {
            if (event.type() != LedgerEventType.DECISION_RECORDED) {
                continue;
            }
            items.add(new DecisionItemView(
                    event.aggregateId(),
                    event.meetingOccurrenceId(),
                    payloadText(event),
                    "APPROVED",
                    List.of(),
                    event.occurredAt().toString(),
                    null,
                    null
            ));
        }
        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toUpperCase(Locale.ROOT);
            items = items.stream().filter(item -> normalized.equals(item.status())).toList();
        }
        return page(items, cursor, limit);
    }

    @GetMapping("/actions")
    @RequiresPermission(Permission.MEETING_READ)
    public PortalCursorPage<ActionItemView> listActions(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit,
            HttpServletResponse response
    ) {
        AuthenticatedPrincipal principal = require(Permission.MEETING_READ);
        Map<UUID, ActionItemResponse> richActions = richActionIndex();
        List<ActionItemView> items = ledgerApi.listActionItems(principal.tenantId()).stream()
                .map(item -> toActionItemView(item, richActions.get(item.id())))
                .filter(item -> matchesActionStatus(item, status))
                .toList();
        return page(items, cursor, limit);
    }

    @PostMapping("/actions/{actionId}/complete")
    @RequiresPermission(Permission.MEETING_WRITE)
    public ActionItemView completeAction(@PathVariable UUID actionId) {
        AuthenticatedPrincipal principal = require(Permission.MEETING_WRITE);
        TenantId tenantId = principal.tenantId();
        if (ledgerApi.findActionItem(tenantId, actionId).isEmpty()) {
            throw new ActenoraException("ACTION_NOT_FOUND", "Action not found: " + actionId);
        }
        ActionItemResponse updated = null;
        if (meetingIntelligenceApi.isPresent()) {
            ActionItemResponse rich = meetingIntelligenceApi.get().listActionItems().stream()
                    .filter(item -> actionId.equals(item.id()))
                    .findFirst()
                    .orElse(null);
            if (rich != null && rich.status() != ActionItemStatus.COMPLETED) {
                updated = meetingIntelligenceApi.get().updateActionItem(
                        actionId,
                        new ActionItemUpdateRequest(null, null, null, ActionItemStatus.COMPLETED, rich.version())
                );
            } else {
                updated = rich;
            }
        }
        ledgerApi.completeActionItem(tenantId, actionId);
        return toActionItemView(ledgerApi.findActionItem(tenantId, actionId).orElseThrow(), updated);
    }

    @GetMapping("/commitments")
    @RequiresPermission(Permission.MEETING_READ)
    public PortalCursorPage<CommitmentItemView> listCommitments(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        AuthenticatedPrincipal principal = require(Permission.MEETING_READ);
        List<CommitmentItemView> items = new ArrayList<>();
        for (CommitmentConfirmation c : ledgerApi.overdueCommitments(principal.tenantId())) {
            items.add(new CommitmentItemView(
                    c.commitmentId(),
                    c.meetingOccurrenceId(),
                    c.text(),
                    c.owner().orElse("unknown"),
                    c.dueDate().map(Object::toString).orElse(null),
                    "AT_RISK",
                    List.of()
            ));
        }
        for (LedgerEvent event : ledgerApi.listEvents(principal.tenantId())) {
            if (event.type() != LedgerEventType.COMMITMENT_RECORDED) {
                continue;
            }
            UUID id = event.aggregateId();
            if (items.stream().anyMatch(i -> i.id().equals(id))) {
                continue;
            }
            items.add(new CommitmentItemView(
                    id,
                    event.meetingOccurrenceId(),
                    payloadText(event),
                    event.optional("owner") == null ? "unknown" : event.optional("owner"),
                    event.optional("dueDate"),
                    "OPEN",
                    List.of()
            ));
        }
        return page(items, cursor, limit);
    }

    @GetMapping("/templates")
    @RequiresPermission(Permission.TEMPLATE_MANAGE)
    public TemplateListView listTemplates(HttpServletResponse response) {
        require(Permission.TEMPLATE_MANAGE);
        if (templateApi.isEmpty()) {
            markStub(response);
            return new TemplateListView(List.of());
        }
        List<TemplateSummaryView> items = templateApi.get().listTemplates(principalTenantId()).stream()
                .map(PortalApiController::toTemplateSummary)
                .toList();
        return new TemplateListView(items);
    }

    @PostMapping("/templates")
    @RequiresPermission(Permission.TEMPLATE_MANAGE)
    public TemplateSummaryView createTemplate(@RequestBody CreateTemplateBody body) {
        require(Permission.TEMPLATE_MANAGE);
        if (body == null || body.name() == null || body.name().isBlank()) {
            throw new ActenoraException("INVALID_TEMPLATE_NAME", "Template name is required");
        }
        TemplateApi api = templateApi.orElseThrow(() ->
                new ActenoraException("TEMPLATE_MODULE_UNAVAILABLE", "Template module is not enabled"));
        var templateId = api.createTemplate(principalTenantId(), body.name().trim());
        String locale = body.locale() == null || body.locale().isBlank() ? "en" : body.locale().trim();
        return new TemplateSummaryView(templateId.value(), body.name().trim(), locale, 0, "DRAFT", false);
    }

    @GetMapping("/templates/{templateId}")
    @RequiresPermission(Permission.TEMPLATE_MANAGE)
    public TemplateDetailView getTemplate(@PathVariable UUID templateId) {
        require(Permission.TEMPLATE_MANAGE);
        TemplateApi api = templateApi.orElseThrow(() ->
                new ActenoraException("TEMPLATE_MODULE_UNAVAILABLE", "Template module is not enabled"));
        MeetingTemplate template = api.getTemplate(principalTenantId(), MeetingTemplateId.of(templateId));
        return toTemplateDetail(template, "en");
    }

    @PostMapping("/templates/{templateId}/versions")
    @RequiresPermission(Permission.TEMPLATE_MANAGE)
    public TemplateVersionView createTemplateDraft(
            @PathVariable UUID templateId,
            @RequestBody CreateTemplateVersionBody body
    ) {
        require(Permission.TEMPLATE_MANAGE);
        TemplateApi api = templateApi.orElseThrow(() ->
                new ActenoraException("TEMPLATE_MODULE_UNAVAILABLE", "Template module is not enabled"));
        String changelog = body == null || body.changelog() == null || body.changelog().isBlank()
                ? "Draft"
                : body.changelog().trim();
        TemplateVersionId versionId = api.createDraftVersion(
                principalTenantId(),
                MeetingTemplateId.of(templateId),
                changelog
        );
        MeetingTemplate template = api.getTemplate(principalTenantId(), MeetingTemplateId.of(templateId));
        TemplateVersion created = template.versions().stream()
                .filter(v -> v.id().equals(versionId))
                .findFirst()
                .orElseThrow(() -> new ActenoraException("VERSION_NOT_FOUND", "Created version not found"));
        return toTemplateVersionView(created);
    }

    @PutMapping("/templates/{templateId}/versions/{versionId}/design")
    @RequiresPermission(Permission.TEMPLATE_MANAGE)
    public TemplateVersionView saveTemplateDesign(
            @PathVariable UUID templateId,
            @PathVariable UUID versionId,
            @RequestBody SaveTemplateDesignBody body
    ) {
        require(Permission.TEMPLATE_MANAGE);
        TemplateApi api = templateApi.orElseThrow(() ->
                new ActenoraException("TEMPLATE_MODULE_UNAVAILABLE", "Template module is not enabled"));
        if (body == null || body.designSchemaJson() == null || body.designSchemaJson().isBlank()) {
            throw new ActenoraException("INVALID_DESIGN", "designSchemaJson is required");
        }
        String contentJson = body.contentSchemaJson() == null ? "" : body.contentSchemaJson();
        api.saveDraftDesign(
                principalTenantId(),
                TemplateVersionId.of(versionId),
                body.designSchemaJson(),
                contentJson
        );
        MeetingTemplate template = api.getTemplate(principalTenantId(), MeetingTemplateId.of(templateId));
        TemplateVersion saved = template.versions().stream()
                .filter(v -> v.id().value().equals(versionId))
                .findFirst()
                .orElseThrow(() -> new ActenoraException("VERSION_NOT_FOUND", "Template version not found"));
        return toTemplateVersionView(saved);
    }

    @PostMapping("/templates/{templateId}/versions/{versionId}/publish")
    @RequiresPermission(Permission.TEMPLATE_MANAGE)
    public TemplateVersionView publishTemplateVersion(
            @PathVariable UUID templateId,
            @PathVariable UUID versionId
    ) {
        require(Permission.TEMPLATE_MANAGE);
        TemplateApi api = templateApi.orElseThrow(() ->
                new ActenoraException("TEMPLATE_MODULE_UNAVAILABLE", "Template module is not enabled"));
        api.publish(principalTenantId(), TemplateVersionId.of(versionId));
        MeetingTemplate template = api.getTemplate(principalTenantId(), MeetingTemplateId.of(templateId));
        TemplateVersion published = template.versions().stream()
                .filter(v -> v.id().value().equals(versionId))
                .findFirst()
                .orElseThrow(() -> new ActenoraException("VERSION_NOT_FOUND", "Template version not found"));
        return toTemplateVersionView(published);
    }

    @PutMapping("/templates/{templateId}/default")
    @RequiresPermission(Permission.TEMPLATE_MANAGE)
    public TemplateDetailView setDefaultTemplate(@PathVariable UUID templateId) {
        require(Permission.TEMPLATE_MANAGE);
        TemplateApi api = templateApi.orElseThrow(() ->
                new ActenoraException("TEMPLATE_MODULE_UNAVAILABLE", "Template module is not enabled"));
        MeetingTemplate template = api.setDefaultTemplate(principalTenantId(), MeetingTemplateId.of(templateId));
        return toTemplateDetail(template, "en");
    }

    @GetMapping("/meetings/{meetingId}/notes/{noteId}/template-lock")
    @RequiresPermission(Permission.MEETING_READ)
    public NoteTemplateLockView getNoteTemplateLock(
            @PathVariable UUID meetingId,
            @PathVariable UUID noteId,
            HttpServletResponse response
    ) {
        require(Permission.MEETING_READ);
        meetingApi.getMeeting(meetingId);
        if (templateApi.isEmpty()) {
            markStub(response);
            return null;
        }
        TemplateApi api = templateApi.get();
        TenantId tenantId = principalTenantId();
        Optional<NoteTemplateLockView> pinned = api.findLockedTemplateVersion(tenantId, noteId)
                .flatMap(versionId -> resolveNoteTemplateLock(tenantId, versionId, true));
        if (pinned.isPresent()) {
            return pinned.get();
        }
        // Not yet pinned: surface the tenant default so a new note starts on the current standard.
        return api.findDefaultTemplate(tenantId)
                .flatMap(template -> template.latestPublished()
                        .map(version -> toNoteTemplateLockView(template, version, false)))
                .orElse(null);
    }

    @PutMapping("/meetings/{meetingId}/notes/{noteId}/template-lock")
    @RequiresPermission(Permission.MEETING_WRITE)
    public NoteTemplateLockView lockNoteTemplate(
            @PathVariable UUID meetingId,
            @PathVariable UUID noteId,
            @RequestBody LockNoteTemplateBody body
    ) {
        require(Permission.MEETING_WRITE);
        meetingApi.getMeeting(meetingId);
        if (body == null || body.templateVersionId() == null) {
            throw new ActenoraException("INVALID_TEMPLATE_LOCK", "templateVersionId is required");
        }
        TemplateApi api = templateApi.orElseThrow(() ->
                new ActenoraException("TEMPLATE_MODULE_UNAVAILABLE", "Template module is not enabled"));
        TemplateVersionId versionId = TemplateVersionId.of(body.templateVersionId());
        api.lockNoteToTemplateVersion(principalTenantId(), noteId, versionId);
        return resolveNoteTemplateLock(principalTenantId(), versionId, true)
                .orElseThrow(() -> new ActenoraException("TEMPLATE_LOCK_FAILED", "Could not resolve template lock"));
    }

    @GetMapping("/teams/settings")
    @RequiresPermission(Permission.TENANT_READ)
    public TeamsSettingsView teamsSettings(HttpServletResponse response) {
        require(Permission.TENANT_READ);
        return buildTeamsSettings(TenantSecurityContext.require().tenantId().value(), response);
    }

    @PutMapping("/teams/settings")
    @RequiresPermission(Permission.TENANT_ADMINISTER)
    public TeamsSettingsView updateTeamsSettings(@RequestBody UpdateTeamsSettingsBody body) {
        AuthenticatedPrincipal principal = require(Permission.TENANT_ADMINISTER);
        if (body != null) {
            teamsPreferencesStore.setAutoJoinEnabled(principal.tenantId().value(), body.autoJoinEnabled());
        }
        return buildTeamsSettings(principal.tenantId().value(), null);
    }

    @GetMapping("/intelligence/connection")
    @RequiresPermission(Permission.MODEL_CONTROL)
    public NanobaseAiConnectionView intelligenceConnection() {
        require(Permission.MODEL_CONTROL);
        return toConnectionView(requireIntelligence().current());
    }

    @PutMapping("/intelligence/connection")
    @RequiresPermission(Permission.MODEL_CONTROL)
    public NanobaseAiConnectionView updateIntelligenceConnection(@RequestBody UpdateIntelligenceConnectionBody body) {
        require(Permission.MODEL_CONTROL);
        NanobaseAiConnectionService.ConnectionView updated = requireIntelligence().update(
                new NanobaseAiConnectionService.UpdateConnectionCommand(
                        body == null ? null : body.baseUrl(),
                        body == null ? null : body.enabled(),
                        body == null ? null : body.servedModelIds()
                )
        );
        return toConnectionView(updated);
    }

    @PostMapping("/intelligence/connection/test")
    @RequiresPermission(Permission.MODEL_CONTROL)
    public NanobaseAiConnectionView testIntelligenceConnection() {
        require(Permission.MODEL_CONTROL);
        return toConnectionView(requireIntelligence().testConnection());
    }

    @GetMapping("/model-control/health")
    @RequiresPermission(Permission.MODEL_CONTROL)
    public ModelHealthView modelHealth(HttpServletResponse response) {
        require(Permission.MODEL_CONTROL);
        if (modelManagementApi.isEmpty()) {
            markStub(response);
            return new ModelHealthView(List.of(), List.of(), new RoutingView("prefer-registry", List.of()));
        }
        return toPortalModelHealth(modelManagementApi.get().healthView(requireModelActor()));
    }

    @GetMapping("/ai-jobs")
    @RequiresPermission(Permission.MEETING_READ)
    public PortalCursorPage<AiJobView> listAiJobs(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit,
            HttpServletResponse response
    ) {
        require(Permission.MEETING_READ);
        if (aiProcessingApi.isEmpty()) {
            markStub(response);
            return page(List.of(), cursor, limit);
        }
        UUID tenantId = TenantSecurityContext.require().tenantId().value();
        Map<UUID, String> meetingTitles = new LinkedHashMap<>();
        List<AiJobView> items = aiProcessingApi.get().listJobsForTenant(tenantId).stream()
                .map(job -> toAiJobView(job, resolveMeetingTitle(job.meetingOccurrenceId(), meetingTitles)))
                .toList();
        return page(items, cursor, limit);
    }

    @GetMapping("/operations/overview")
    @RequiresPermission(Permission.OPERATIONS_MANAGE)
    public OperationsOverviewView operationsOverview(HttpServletResponse response) {
        require(Permission.OPERATIONS_MANAGE);
        if (operationsApi.isPresent()) {
            OperationsViews.QueueDashboardView dashboard = operationsApi.get().queueDashboard();
            OperationsViews.WorkerHealthView workers = operationsApi.get().workerHealth();
            return new OperationsOverviewView(
                    (int) dashboard.aiQueueDepth()
                            + transcriptPollScheduler.map(s -> (int) s.pendingCount()).orElse(0),
                    (int) dashboard.dlqDepth(),
                    graphObservability
                            .map(graph -> List.<Object>of(
                                    new CircuitBreakerView("microsoft-graph", graph.circuitState())))
                            .orElse(List.of()),
                    workers.workers().stream()
                            .map(w -> new WorkerSummaryView(w.workerId(), w.lastHealthStatus()))
                            .map(w -> (Object) w)
                            .toList()
            );
        }
        markStub(response);
        return new OperationsOverviewView(0, 0, List.of(), List.of());
    }

    @GetMapping("/audit/events")
    @RequiresPermission(Permission.AUDIT_READ)
    public PortalCursorPage<AuditEventView> listAuditEvents(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit,
            HttpServletResponse response
    ) {
        require(Permission.AUDIT_READ);
        if (auditApi.isEmpty()) {
            markStub(response);
            return page(List.of(), cursor, limit);
        }
        UUID tenantId = TenantSecurityContext.require().tenantId().value();
        PortalAuditEventPresenter presenter = new PortalAuditEventPresenter(identityApi, meetingApi);
        Map<UUID, String> actorNames = PortalAuditEventPresenter.preloadActorNames(tenantId, identityApi);
        List<AuditEventView> items = auditApi.get().listForTenant(tenantId).stream()
                .sorted((a, b) -> b.occurredAt().compareTo(a.occurredAt()))
                .map(entry -> presenter.present(entry, tenantId, actorNames))
                .toList();
        return page(items, cursor, limit);
    }

    private static void markStub(HttpServletResponse response) {
        if (response != null) {
            response.setHeader(COMPOSITION_STUB_HEADER, "stub");
        }
    }

    private int countPendingApprovals(UUID tenantId) {
        return (int) approvalApi.listForTenant(tenantId).stream()
                .filter(PortalApiController::isPendingApproval)
                .count();
    }

    private int resolveRunningJobs(UUID tenantId) {
        if (operationsApi.isPresent()) {
            return (int) operationsApi.get().queueDashboard().aiQueueDepth();
        }
        if (aiProcessingApi.isEmpty()) {
            return 0;
        }
        return (int) aiProcessingApi.get().listJobsForTenant(tenantId).stream()
                .filter(job -> job.status().isActive())
                .count();
    }

    private static boolean isPendingApproval(ApprovalRequestView view) {
        return view.status() == ApprovalRequestStatus.PENDING
                || view.status() == ApprovalRequestStatus.CHANGES_REQUESTED;
    }

    private static ApprovalRecordView toPendingApprovalRecord(ApprovalRequestView view, UUID noteId) {
        return new ApprovalRecordView(
                view.id().value(),
                view.subjectType().name(),
                noteId,
                "PENDING",
                null,
                null,
                null
        );
    }

    private static final class MutablePendingApprovalGroup {
        private final UUID meetingId;
        private final String meetingTitle;
        private final List<ApprovalRecordView> items = new ArrayList<>();

        private MutablePendingApprovalGroup(UUID meetingId, String meetingTitle) {
            this.meetingId = meetingId;
            this.meetingTitle = meetingTitle;
        }
    }

    private static ActionItemView toActionItemView(
            com.nanobaseai.actenora.meetingintelligence.domain.ledger.projection.LedgerProjectionState.TrackedActionItem item
    ) {
        return toActionItemView(item, null);
    }

    private static ActionItemView toActionItemView(
            com.nanobaseai.actenora.meetingintelligence.domain.ledger.projection.LedgerProjectionState.TrackedActionItem item,
            ActionItemResponse rich
    ) {
        String dueAt = null;
        if (rich != null) {
            dueAt = rich.dueAt() != null
                    ? rich.dueAt().toString()
                    : rich.dueDate() == null ? null : rich.dueDate().toString();
        }
        return new ActionItemView(
                item.id(),
                item.meetingOccurrenceId(),
                rich == null ? item.text() : rich.text(),
                rich == null || rich.status() == null ? item.status().name() : rich.status().name(),
                rich == null || rich.owner() == null || rich.owner().isBlank() ? "unknown" : rich.owner(),
                dueAt,
                List.of(),
                rich == null ? null : blankToNull(rich.ownerType()),
                rich == null ? null : blankToNull(rich.priority()),
                rich == null ? null : blankToNull(rich.relativeDate())
        );
    }

    private Map<UUID, ActionItemResponse> richActionIndex() {
        if (meetingIntelligenceApi.isEmpty()) {
            return Map.of();
        }
        return meetingIntelligenceApi.get().listActionItems().stream()
                .collect(Collectors.toMap(
                        ActionItemResponse::id,
                        java.util.function.Function.identity(),
                        (left, right) -> right
                ));
    }

    private Map<String, TranscriptSegmentView> loadTranscriptSegmentsById(UUID meetingId) {
        if (transcriptApi.isEmpty()) {
            return Map.of();
        }
        try {
            List<TranscriptSegmentView> segments =
                    transcriptApi.get().listSegmentsForMeeting(principalTenantId(), meetingId);
            Map<String, TranscriptSegmentView> byId = new LinkedHashMap<>();
            for (TranscriptSegmentView segment : segments) {
                if (segment == null || segment.id() == null) {
                    continue;
                }
                byId.put(segment.id().toString(), segment);
            }
            return byId;
        } catch (TranscriptDomainException ex) {
            if ("TRANSCRIPT_NOT_FOUND".equals(ex.code())) {
                return Map.of();
            }
            throw ex;
        }
    }

    private static Map<UUID, List<PortalEvidenceView>> indexEvidence(
            MeetingNoteDetailResponse note,
            Map<String, TranscriptSegmentView> segmentsById
    ) {
        Map<UUID, List<PortalEvidenceView>> bySubject = new LinkedHashMap<>();
        if (note.evidenceLinks() == null) {
            return bySubject;
        }
        for (var link : note.evidenceLinks()) {
            if (link == null || link.subjectId() == null || link.evidenceSegmentId() == null) {
                continue;
            }
            String segmentId = link.evidenceSegmentId().trim();
            TranscriptSegmentView segment = segmentsById.get(segmentId);
            if (segment == null) {
                // Case-insensitive fallback for UUID string mismatches.
                for (Map.Entry<String, TranscriptSegmentView> entry : segmentsById.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(segmentId)) {
                        segment = entry.getValue();
                        segmentId = entry.getKey();
                        break;
                    }
                }
            }
            if (segment == null) {
                // Plan §7: never emit seek-to-zero placeholders for unresolved links.
                continue;
            }
            long startMs = segment.startMs();
            long endMs = segment.endMs();
            String quote = segment.text() == null ? "" : truncateQuote(segment.text());
            bySubject.computeIfAbsent(link.subjectId(), ignored -> new ArrayList<>())
                    .add(new PortalEvidenceView(segmentId, startMs, endMs, quote));
        }
        return bySubject;
    }

    private static String truncateQuote(String text) {
        String trimmed = text.trim();
        if (trimmed.length() <= 180) {
            return trimmed;
        }
        return trimmed.substring(0, 177) + "...";
    }

    private static String decisionStatus(com.nanobaseai.actenora.meetingintelligence.api.dto.DecisionResponse decision) {
        if (decision.requiresManualReview()) {
            return "PENDING";
        }
        if (decision.humanApprovalStatus() != null) {
            return switch (decision.humanApprovalStatus().name()) {
                case "APPROVED" -> "APPROVED";
                case "REJECTED" -> "REJECTED";
                default -> "DRAFT";
            };
        }
        return "DRAFT";
    }

    private static String riskSeverity(Double confidence) {
        if (confidence == null) {
            return "MEDIUM";
        }
        if (confidence >= 0.85d) {
            return "HIGH";
        }
        if (confidence >= 0.6d) {
            return "MEDIUM";
        }
        return "LOW";
    }

    static String renderDraftMinutesBody(MeetingNoteDetailResponse note, String meetingTitle) {
        String summary = note.currentVersion() == null || note.currentVersion().executiveSummary() == null
                ? ""
                : note.currentVersion().executiveSummary().trim();
        StringBuilder sb = new StringBuilder();
        sb.append("TOPLANTI TUTANAĞI").append('\n');
        if (meetingTitle != null && !meetingTitle.isBlank()) {
            sb.append("Toplantı Başlığı: ").append(meetingTitle.trim()).append('\n');
        }
        String approval = note.currentVersion() == null || note.currentVersion().approvalStatus() == null
                ? "DRAFT"
                : note.currentVersion().approvalStatus().name();
        if ("DRAFT".equals(approval)) {
            sb.append("Durum: ").append(NanobaseAiBrandSanitizer.draftStatusLabel()).append('\n');
        }
        sb.append('\n').append("1. YÖNETİCİ ÖZETİ").append('\n');
        sb.append(summary.isBlank() ? "—" : summary).append('\n');

        appendMinutesSection(sb, "2. GÜNDEM",
                com.nanobaseai.actenora.security.delivery.ApprovedNoteContentJsonMapper.parseAgendaItems(summary));

        // Deterministic presentation IDs: list order = extraction/persist order (not alphabetical).
        List<String> decisions = new ArrayList<>();
        if (note.decisions() != null) {
            int i = 0;
            for (var d : note.decisions()) {
                if (d == null || d.text() == null || d.text().isBlank()) {
                    continue;
                }
                i++;
                StringBuilder line = new StringBuilder(corporateId("K", i))
                        .append(" — ").append(d.text().trim());
                if (d.rationale() != null && !d.rationale().isBlank()) {
                    line.append(" (Gerekçe: ").append(d.rationale().trim()).append(')');
                }
                if (d.decisionStatus() != null && !d.decisionStatus().isBlank()) {
                    line.append(" [").append(d.decisionStatus().trim()).append(']');
                }
                decisions.add(line.toString());
            }
        }
        appendMinutesSection(sb, "3. ALINAN KARARLAR", decisions);

        List<String> actions = new ArrayList<>();
        boolean anyStructuredMissing = false;
        boolean anyStructuredDue = false;
        boolean anyDateCueWithoutStructured = false;
        boolean anyUnresolvedRelative = false;
        if (note.actionItems() != null) {
            int i = 0;
            for (var a : note.actionItems()) {
                if (a == null || a.text() == null || a.text().isBlank()) {
                    continue;
                }
                i++;
                String owner = a.owner() == null || a.owner().isBlank() ? "—" : a.owner().trim();
                String due;
                Instant dueAtInstant = a.dueAt();
                if (dueAtInstant != null) {
                    anyStructuredDue = true;
                    due = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                            .withZone(java.time.ZoneId.of("Europe/Istanbul"))
                            .format(dueAtInstant);
                } else if (a.relativeDate() != null && !a.relativeDate().isBlank()) {
                    due = a.relativeDate().trim();
                    anyUnresolvedRelative = true;
                } else if (a.dueDate() != null) {
                    anyStructuredDue = true;
                    due = a.dueDate().toString();
                } else {
                    due = "—";
                    anyStructuredMissing = true;
                    if (containsDateCue(a.text())) {
                        anyDateCueWithoutStructured = true;
                    }
                }
                StringBuilder line = new StringBuilder(corporateId("A", i))
                        .append(" — ").append(a.text().trim())
                        .append(" (Sorumlu: ").append(owner).append(", Son tarih: ").append(due);
                if (a.ownerType() != null && !a.ownerType().isBlank()) {
                    line.append(", Tip: ").append(a.ownerType().trim());
                }
                if (a.priority() != null && !a.priority().isBlank()) {
                    line.append(", Öncelik: ").append(a.priority().trim());
                }
                actions.add(line.append(')').toString());
            }
        }
        appendMinutesSection(sb, "4. AKSİYON MADDELERİ", actions);
        if (!actions.isEmpty()) {
            if (anyDateCueWithoutStructured) {
                sb.append("Not: Aksiyon metninde tarih ifadesi bulundu ancak yapılandırılmış son tarih çözümlenemedi.")
                        .append('\n');
            } else if (anyUnresolvedRelative) {
                sb.append("Not: Tarih henüz takvim değerine çözümlenemedi.").append('\n');
            } else if (anyStructuredMissing && !anyStructuredDue) {
                sb.append("Not: Aksiyonlar için yapılandırılmış son tarih bulunmuyor.").append('\n');
            }
        }

        List<String> risks = new ArrayList<>();
        if (note.risks() != null) {
            int i = 0;
            for (var r : note.risks()) {
                if (r == null || r.text() == null || r.text().isBlank()) {
                    continue;
                }
                i++;
                StringBuilder line = new StringBuilder(corporateId("R", i))
                        .append(" — ").append(r.text().trim());
                if (r.likelihood() != null && !r.likelihood().isBlank()) {
                    line.append(" (Olasılık: ").append(r.likelihood().trim()).append(')');
                }
                if (r.mitigation() != null && !r.mitigation().isBlank()) {
                    line.append(" Azaltma: ").append(r.mitigation().trim());
                }
                risks.add(line.toString());
            }
        }
        appendMinutesSection(sb, "5. RİSKLER", risks);
        appendMinutesSection(sb, "6. TAAHHÜTLER", note.commitments() == null ? List.of()
                : note.commitments().stream()
                .filter(Objects::nonNull)
                .map(c -> {
                    String text = c.text() == null ? "" : c.text().trim();
                    String owner = c.owner() == null || c.owner().isBlank() ? null : c.owner().trim();
                    if (owner == null) {
                        return text;
                    }
                    return text + " (Sorumlu: " + owner + ")";
                })
                .filter(t -> t != null && !t.isBlank())
                .toList());
        appendMinutesSection(sb, "7. AÇIK SORULAR", note.openQuestions() == null ? List.of()
                : note.openQuestions().stream().map(q -> q.text()).filter(Objects::nonNull).toList());
        appendMinutesSection(sb, "8. SORUNLAR", note.issues() == null ? List.of()
                : note.issues().stream().map(i -> i.text()).filter(Objects::nonNull).toList());
        appendMinutesSection(sb, "9. ÖNERİLER — HENÜZ KARAR DEĞİL", note.proposals() == null ? List.of()
                : note.proposals().stream().map(p -> p.text()).filter(Objects::nonNull).toList());
        appendMinutesSection(sb, "10. ÖNEMLİ BULGULAR", note.importantFacts() == null ? List.of()
                : note.importantFacts().stream().map(f -> f.text()).filter(Objects::nonNull).toList());
        return sb.toString().trim();
    }

    /** Presentation-only corporate id (K-01 / A-01 / R-01); not persisted. */
    private static String corporateId(String prefix, int oneBasedIndex) {
        return prefix + "-" + String.format(java.util.Locale.ROOT, "%02d", oneBasedIndex);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static boolean containsDateCue(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String n = text.toLowerCase(java.util.Locale.ROOT)
                .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
                .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c');
        return n.contains("bugun")
                || n.contains("yarin")
                || n.contains("hafta sonu")
                || n.contains("kadar") && n.matches(".*\\d{1,2}([.:]\\d{2})?.*")
                || n.contains("pazartesi")
                || n.contains("cuma");
    }

    /**
     * Maps note quality flags to portal labels. Bare {@code OTHER} (and internal LLM/SV/PV tokens)
     * are omitted; pipeline tokens stored in {@code OTHER.detail} are promoted to the detail label.
     */
    static String portalQualityFlagLabel(String codeName, String detail) {
        if (codeName == null || codeName.isBlank()) {
            return null;
        }
        String code = codeName.trim().toUpperCase(java.util.Locale.ROOT);
        if (!"OTHER".equals(code)) {
            return code;
        }
        if (detail == null || detail.isBlank()) {
            return null;
        }
        String normalized = detail.trim().toUpperCase(java.util.Locale.ROOT);
        if (isInternalQualityFlagToken(normalized)) {
            return null;
        }
        return normalized;
    }

    /** Ops/version tokens that must not surface as primary meeting quality chips. */
    private static boolean isInternalQualityFlagToken(String normalized) {
        // DECISION_SUBSUMED_PROPOSAL_DROPPED is intentionally kept for optional admin soft-info.
        return normalized.contains("LLM")
                || normalized.startsWith("SV-")
                || normalized.startsWith("PV-")
                || "OTHER".equals(normalized)
                || normalized.startsWith("AUDITSTATUS=")
                || normalized.startsWith("UNRESOLVEDCONFLICTCOUNT=")
                || normalized.startsWith("GENERICACTIONCOUNT=")
                || normalized.startsWith("UNSUPPORTEDITEMCOUNT=")
                || normalized.startsWith("FALLBACKUSED=")
                || "CONSISTENCY_AUDIT_PASSED".equals(normalized);
    }

    private static void appendMinutesSection(StringBuilder sb, String title, List<String> items) {
        sb.append('\n').append(title).append('\n');
        if (items == null || items.isEmpty()) {
            sb.append("—").append('\n');
            return;
        }
        int i = 1;
        for (String item : items) {
            if (item == null || item.isBlank()) {
                continue;
            }
            sb.append(i++).append(". ").append(item.trim()).append('\n');
        }
        if (i == 1) {
            sb.append("—").append('\n');
        }
    }

    private String resolveMeetingTitle(UUID meetingId, Map<UUID, String> cache) {
        if (meetingId == null) {
            return "";
        }
        String cached = cache.get(meetingId);
        if (cached != null) {
            return cached;
        }
        String title = "";
        try {
            MeetingResponse meeting = meetingApi.getMeeting(meetingId);
            if (meeting.title() != null && !meeting.title().isBlank()) {
                title = meeting.title().trim();
            }
        } catch (RuntimeException ignored) {
            /* meeting may have been deleted */
        }
        cache.put(meetingId, title);
        return title;
    }

    private static AiJobView toAiJobView(AiJob job, String meetingTitle) {
        return new AiJobView(
                job.id(),
                job.meetingOccurrenceId(),
                meetingTitle,
                job.status().name(),
                job.taskType(),
                job.startedAt().map(Instant::toString).orElse(job.queuedAt().toString()),
                job.completedAt().map(Instant::toString).orElse(null)
        );
    }

    private TeamsSettingsView buildTeamsSettings(UUID tenantId, HttpServletResponse response) {
        List<GraphSubscription> subscriptions = microsoftConnectionApi
                .map(api -> api.listSubscriptions(tenantId))
                .orElse(List.of());
        if (microsoftConnectionApi.isEmpty()) {
            markStub(response);
            return new TeamsSettingsView(false, graphClientId, "not_configured", teamsPreferencesStore.autoJoinEnabled(tenantId));
        }
        boolean connected = !subscriptions.isEmpty();
        String webhookStatus = resolveWebhookStatus(subscriptions);
        if ("active".equals(webhookStatus) && graphObservability.isPresent()) {
            String observed = graphObservability.get().webhookStatus();
            if ("degraded".equals(observed)) {
                webhookStatus = observed;
            }
        }
        String appId = subscriptions.stream()
                .map(GraphSubscription::applicationId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst()
                .orElse(graphClientId);
        return new TeamsSettingsView(
                connected,
                appId == null ? "" : appId,
                webhookStatus,
                teamsPreferencesStore.autoJoinEnabled(tenantId)
        );
    }

    private static String resolveWebhookStatus(List<GraphSubscription> subscriptions) {
        if (subscriptions.isEmpty()) {
            return "not_configured";
        }
        Instant now = Instant.now();
        boolean active = subscriptions.stream().anyMatch(sub -> sub.expirationDateTime().isAfter(now));
        return active ? "active" : "expired";
    }

    private static TemplateSummaryView toTemplateSummary(MeetingTemplate template) {
        Optional<TemplateVersion> latest = template.versions().stream()
                .max(Comparator.comparingInt(TemplateVersion::versionNumber));
        int versionNumber = latest.map(TemplateVersion::versionNumber).orElse(0);
        String status;
        if (template.publishedVersionId().isPresent()) {
            status = TemplateVersionStatus.PUBLISHED.name();
        } else if (latest.isPresent()) {
            status = latest.get().status().name();
        } else {
            status = TemplateVersionStatus.DRAFT.name();
        }
        return new TemplateSummaryView(
                template.id().value(),
                template.name(),
                "en",
                versionNumber,
                status,
                template.isDefault()
        );
    }

    private static TemplateDetailView toTemplateDetail(MeetingTemplate template, String locale) {
        List<TemplateVersionView> versions = template.versions().stream()
                .sorted(Comparator.comparingInt(TemplateVersion::versionNumber).reversed())
                .map(PortalApiController::toTemplateVersionView)
                .toList();
        return new TemplateDetailView(
                template.id().value(),
                template.name(),
                locale,
                versions,
                template.publishedVersionId().map(TemplateVersionId::value).orElse(null),
                template.isDefault()
        );
    }

    private static TemplateVersionView toTemplateVersionView(TemplateVersion version) {
        DesignSchemaView design = version.designSchema()
                .map(PortalApiController::toDesignSchemaView)
                .orElse(null);
        return new TemplateVersionView(
                version.id().value(),
                version.versionNumber(),
                version.status().name(),
                version.changelog(),
                version.updatedAt().toString(),
                design
        );
    }

    private static DesignSchemaView toDesignSchemaView(DesignSchema schema) {
        List<DesignComponentView> components = schema.components().stream()
                .map(PortalApiController::toDesignComponentView)
                .toList();
        return new DesignSchemaView(schema.schemaVersion(), schema.pageSize(), components);
    }

    private static DesignComponentView toDesignComponentView(DesignComponent component) {
        return new DesignComponentView(
                component.id().toString(),
                component.type().name(),
                component.order(),
                component.props()
        );
    }

    private Optional<NoteTemplateLockView> resolveNoteTemplateLock(
            TenantId tenantId,
            TemplateVersionId versionId,
            boolean locked
    ) {
        if (templateApi.isEmpty()) {
            return Optional.empty();
        }
        for (MeetingTemplate template : templateApi.get().listTemplates(tenantId)) {
            for (TemplateVersion version : template.versions()) {
                if (version.id().equals(versionId)) {
                    return Optional.of(toNoteTemplateLockView(template, version, locked));
                }
            }
        }
        return Optional.empty();
    }

    private static NoteTemplateLockView toNoteTemplateLockView(
            MeetingTemplate template,
            TemplateVersion version,
            boolean locked
    ) {
        return new NoteTemplateLockView(
                template.id().value(),
                template.name(),
                version.id().value(),
                version.versionNumber(),
                locked,
                version.designSchema().map(PortalApiController::toDesignSchemaView).orElse(null)
        );
    }

    private ModelHealthView toPortalModelHealth(
            com.nanobaseai.actenora.modelmanagement.application.ModelHealthView health
    ) {
        List<ModelSummaryView> models = new ArrayList<>();
        List<DeploymentSummaryView> deployments = new ArrayList<>();
        for (var entry : health.models()) {
            String displayName = entry.modelKey();
            if (modelManagementApi.isPresent()) {
                try {
                    displayName = modelManagementApi.get().getModel(entry.modelKey()).displayName();
                } catch (RuntimeException ignored) {
                    /* keep modelKey */
                }
            }
            models.add(new ModelSummaryView(
                    NanobaseAiBrandSanitizer.displayModelName(entry.modelKey()),
                    NanobaseAiBrandSanitizer.displayModelName(displayName),
                    entry.acceptingNewWork(),
                    entry.status().name()
            ));
            for (var deployment : entry.deployments()) {
                boolean healthy = deployment.status() == DeploymentStatus.HEALTHY && !deployment.heartbeatTimedOut();
                deployments.add(new DeploymentSummaryView(
                        NanobaseAiBrandSanitizer.displayModelName(deployment.deploymentKey()),
                        NanobaseAiBrandSanitizer.displayModelName(entry.modelKey()),
                        NanobaseAiBrandSanitizer.displayModelName(deployment.deploymentKey()),
                        healthy
                ));
            }
        }
        return new ModelHealthView(models, deployments, new RoutingView("prefer-registry", routingRoles(models)));
    }

    private static List<RoleRoutingView> routingRoles(List<ModelSummaryView> models) {
        if (models.isEmpty()) {
            return List.of();
        }
        return models.stream()
                .map(model -> new RoleRoutingView("inference", model.modelKey(), model.modelKey()))
                .toList();
    }

    private String resolveBusinessContextName(UUID businessContextId) {
        if (businessContextId == null) {
            return null;
        }
        try {
            for (BusinessContextResponse context : meetingApi.listBusinessContexts()) {
                if (context.id().equals(businessContextId)
                        && context.name() != null
                        && !context.name().isBlank()) {
                    return context.name();
                }
            }
        } catch (RuntimeException ignored) {
            /* fall through */
        }
        return null;
    }

    private static boolean matchesActionStatus(ActionItemView item, String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        return status.trim().toUpperCase(Locale.ROOT).equals(item.status());
    }

    private ActorPrincipal requireModelActor() {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        String role = principal.roles().stream().findFirst().orElse("OPERATIONS");
        return ActorPrincipal.of(principal.userId(), role, EnumSet.allOf(ModelControlPermission.class));
    }

    private NanobaseAiConnectionService requireIntelligence() {
        return nanobaseAiConnectionService.orElseThrow(() ->
                new ActenoraException(
                        "NANOBASEAI_UNAVAILABLE",
                        "NanobaseAI Intelligence settings are not available on this runtime"
                ));
    }

    private void requireNoteBelongsToMeeting(UUID meetingId, UUID noteId) {
        if (meetingIntelligenceApi.isEmpty()) {
            return;
        }
        MeetingNoteDetailResponse note = meetingIntelligenceApi.get().getNoteDetail(noteId);
        if (!meetingId.equals(note.meetingOccurrenceId())) {
            throw new ActenoraException("NOTE_MEETING_MISMATCH", "Note does not belong to this meeting");
        }
    }

    private static String portalNotePdfPath(UUID meetingId, UUID noteId) {
        return "/api/v1/portal/meetings/" + meetingId + "/notes/" + noteId + "/pdf";
    }

    private Optional<ResolvedNotePdf> resolveNotePdf(TenantId tenantId, UUID noteId) {
        if (templateApi.isPresent()) {
            Optional<RenderedDocumentView> templatePdf = templateApi.get()
                    .listRenderedDocumentsForNote(tenantId, noteId)
                    .stream()
                    .filter(doc -> "PDF".equalsIgnoreCase(doc.format()))
                    .max(Comparator.comparing(
                            doc -> doc.createdAt() == null ? Instant.EPOCH : doc.createdAt()));
            if (templatePdf.isPresent()) {
                RenderedDocumentView doc = templatePdf.get();
                return Optional.of(new ResolvedNotePdf(
                        doc.id(),
                        doc.storageKey(),
                        doc.sizeBytes(),
                        doc.createdAt()
                ));
            }
        }
        return resolveFallbackPdf(tenantId, noteId);
    }

    private Optional<ResolvedNotePdf> resolveFallbackPdf(TenantId tenantId, UUID noteId) {
        if (meetingIntelligenceApi.isEmpty() || objectStorage.isEmpty()) {
            return Optional.empty();
        }
        MeetingNoteDetailResponse detail = meetingIntelligenceApi.get().getNoteDetail(noteId);
        if (detail.currentVersion() == null || detail.currentVersion().id() == null) {
            return Optional.empty();
        }
        UUID versionId = detail.currentVersion().id();
        String storageKey = "tenants/" + tenantId.value() + "/notes/" + versionId + "/fallback.pdf";
        if (!objectStorage.get().exists(storageKey)) {
            return Optional.empty();
        }
        Optional<ObjectMetadata> meta = objectStorage.get().metadata(storageKey);
        long sizeBytes = meta.map(ObjectMetadata::sizeBytes).orElse(0L);
        Instant createdAt = meta.map(ObjectMetadata::lastModified).orElse(null);
        UUID documentId = UUID.nameUUIDFromBytes(storageKey.getBytes(StandardCharsets.UTF_8));
        return Optional.of(new ResolvedNotePdf(documentId, storageKey, sizeBytes, createdAt));
    }

    private record ResolvedNotePdf(
            UUID documentId,
            String storageKey,
            long sizeBytes,
            Instant createdAt
    ) {
    }

    private static NanobaseAiConnectionView toConnectionView(NanobaseAiConnectionService.ConnectionView view) {
        return new NanobaseAiConnectionView(
                view.productName(),
                view.mode(),
                view.enabled(),
                view.endpointHost(),
                view.baseUrl(),
                view.healthy(),
                view.latencyMs(),
                view.statusDetail(),
                List.copyOf(view.servedModelIds()),
                view.checkedAt().toString()
        );
    }

    private AuthenticatedPrincipal require(Permission permission) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, permission);
        return principal;
    }

    private List<MeetingSummaryView> toSummaries(List<MeetingResponse> meetings) {
        List<MeetingSummaryView> out = new ArrayList<>();
        for (MeetingResponse meeting : meetings) {
            int count;
            try {
                count = meetingApi.listParticipants(meeting.id()).size();
            } catch (RuntimeException ex) {
                count = 0;
            }
            out.add(toSummary(meeting, count));
        }
        return out;
    }

    private static MeetingSummaryView toSummary(MeetingResponse meeting, int participantCount) {
        return new MeetingSummaryView(
                meeting.id(),
                meeting.title() == null ? "(untitled)" : meeting.title(),
                meeting.status() == null ? "SCHEDULED" : meeting.status().name(),
                meeting.scheduledStartAt() == null ? Instant.EPOCH.toString() : meeting.scheduledStartAt().toString(),
                participantCount
        );
    }

    private static MeetingOccurrenceStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return MeetingOccurrenceStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ActenoraException("INVALID_STATUS_FILTER", "Unknown status: " + status);
        }
    }

    private static ApprovalDecisionType parseDecision(String decision) {
        if (decision == null || decision.isBlank()) {
            throw new ActenoraException("INVALID_APPROVAL_DECISION", "decision is required");
        }
        return switch (decision.trim().toUpperCase(Locale.ROOT)) {
            case "APPROVE", "APPROVED", "GRANTED" -> ApprovalDecisionType.APPROVE;
            case "REJECT", "REJECTED", "DENIED" -> ApprovalDecisionType.REJECT;
            case "REQUEST_CHANGES", "CHANGES_REQUESTED" -> ApprovalDecisionType.REQUEST_CHANGES;
            default -> throw new ActenoraException(
                    "INVALID_APPROVAL_DECISION", "Unsupported decision: " + decision);
        };
    }

    private static ApprovalRecordView toApprovalRecord(
            ApprovalRequestView view,
            UUID noteId,
            String actorDisplayName
    ) {
        String portalStatus = switch (view.status()) {
            case PENDING, CHANGES_REQUESTED -> "PENDING";
            case GRANTED -> "APPROVED";
            case DENIED, EXPIRED -> "REJECTED";
        };
        boolean decided = view.status() != ApprovalRequestStatus.PENDING
                && view.status() != ApprovalRequestStatus.CHANGES_REQUESTED;
        return new ApprovalRecordView(
                view.id().value(),
                view.subjectType().name(),
                noteId != null ? noteId : view.subjectId(),
                portalStatus,
                decided ? actorDisplayName : null,
                decided ? view.updatedAt().toString() : null,
                null
        );
    }

    private static String payloadText(LedgerEvent event) {
        String text = event.optional("text");
        if (text != null && !text.isBlank()) {
            return text;
        }
        String newer = event.optional("newerText");
        if (newer != null && !newer.isBlank()) {
            return newer;
        }
        return event.type().name();
    }

    private static <T> PortalCursorPage<T> page(List<T> items, String cursor, Integer limit) {
        int start = 0;
        if (cursor != null && !cursor.isBlank()) {
            try {
                start = Integer.parseInt(cursor);
            } catch (NumberFormatException ignored) {
                start = 0;
            }
        }
        int size = limit == null || limit < 1 ? 50 : Math.min(limit, 200);
        if (start >= items.size()) {
            return new PortalCursorPage<>(List.of(), null);
        }
        int end = Math.min(start + size, items.size());
        String next = end < items.size() ? Integer.toString(end) : null;
        return new PortalCursorPage<>(items.subList(start, end), next);
    }

    @ExceptionHandler(ActenoraException.class)
    public ResponseEntity<ProblemDetail> handle(ActenoraException ex) {
        HttpStatus status = switch (ex.code()) {
            case "ACTION_NOT_FOUND",
                 "INTELLIGENCE_RESOURCE_NOT_FOUND",
                 "MEETING_NOTE_NOT_FOUND",
                 "MEETING_NOT_FOUND",
                 "PDF_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "UNAUTHORIZED_MEETING_ACCESS",
                 "PRIVATE_NOTE_ACCESS_DENIED",
                 "PRIVATE_NOTE_AI_ACCESS_DENIED" -> HttpStatus.FORBIDDEN;
            case "NOTE_UPDATE_UNAVAILABLE" -> HttpStatus.NOT_IMPLEMENTED;
            case "NANOBASEAI_UNREACHABLE",
                 "NANOBASEAI_ENDPOINT_DENIED",
                 "NANOBASEAI_REQUIRED" -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status,
                NanobaseAiBrandSanitizer.sanitize(ex.getMessage())
        );
        problem.setTitle(ex.code());
        problem.setProperty("code", ex.code());
        return ResponseEntity.status(status).body(problem);
    }

    public record PortalUserView(
            UUID id,
            String displayName,
            String email,
            String role,
            UUID tenantId,
            List<String> permissions
    ) {
    }

    public record DashboardView(
            int pendingApprovals,
            int openActions,
            int overdueCommitments,
            int runningJobs,
            List<MeetingSummaryView> recentMeetings
    ) {
    }

    public record PortalNotificationFeedView(
            List<PortalNotificationItemView> items,
            int unreadCount
    ) {
    }

    public record PortalNotificationItemView(
            UUID id,
            String type,
            String title,
            String body,
            String href,
            String createdAt,
            String readAt
    ) {
    }

    public record MeetingSummaryView(
            UUID id,
            String title,
            String status,
            String scheduledStartAt,
            int participantCount
    ) {
    }

    public record PortalCursorPage<T>(List<T> items, String nextCursor) {
    }

    public record PortalParticipantView(
            UUID id,
            String displayName,
            String email,
            String participantType,
            String attendanceStatus,
            boolean external
    ) {
    }

    public record MeetingDetailView(
            MeetingSummaryView meeting,
            List<PortalParticipantView> participants,
            String seriesTitle,
            String businessContext,
            List<MeetingVersionView> versions,
            List<ApprovalRecordView> approvalHistory,
            List<MeetingNoteView> notes,
            List<DecisionItemView> decisions,
            List<ActionItemView> actions,
            List<RiskItemView> risks,
            List<CommitmentItemView> commitments,
            List<String> qualityFlags,
            boolean partial
    ) {
    }

    public record MeetingVersionView(int version, String label, String createdAt) {
    }

    public record TranscriptView(List<? extends Object> segments, List<String> speakers) {
    }

    public record PortalDeliveryRequestView(
            UUID id,
            UUID noteVersionId,
            String intent,
            String status,
            String recipientEmail,
            String createdAt,
            String updatedAt
    ) {
    }

    public record PortalRenderJobView(
            UUID id,
            String format,
            String status,
            UUID renderedDocumentId,
            String createdAt,
            String updatedAt,
            String lastError
    ) {
    }

    public record PortalRenderedDocumentView(
            UUID id,
            String format,
            long sizeBytes,
            String downloadUrl,
            String expiresAt,
            String createdAt
    ) {
    }

    public record NoteRenderStatusView(
            List<PortalRenderJobView> jobs,
            List<PortalRenderedDocumentView> documents
    ) {
    }

    public record MeetingNoteView(
            UUID id,
            String visibility,
            String body,
            String updatedAt,
            UUID authorId,
            String approvalStatus,
            boolean draft,
            long version
    ) {
    }

    public record DecisionItemView(
            UUID id,
            UUID meetingId,
            String title,
            String status,
            List<PortalEvidenceView> evidence,
            String createdAt,
            String rationale,
            String decisionStatus
    ) {
    }

    public record ActionItemView(
            UUID id,
            UUID meetingId,
            String title,
            String status,
            String ownerDisplayName,
            String dueAt,
            List<PortalEvidenceView> evidence,
            String ownerType,
            String priority,
            String relativeDate
    ) {
    }

    public record RiskItemView(
            UUID id,
            String title,
            String severity,
            List<PortalEvidenceView> evidence,
            String likelihood,
            String mitigation
    ) {
    }

    public record CommitmentItemView(
            UUID id,
            UUID meetingId,
            String statement,
            String ownerDisplayName,
            String dueAt,
            String status,
            List<PortalEvidenceView> evidence
    ) {
    }

    public record PortalEvidenceView(
            String segmentId,
            long startMs,
            long endMs,
            String quote
    ) {
    }

    public record ApprovalRecordView(
            UUID id,
            String artifactType,
            UUID artifactId,
            String status,
            String decidedBy,
            String decidedAt,
            String comment
    ) {
    }

    public record PendingApprovalGroupView(
            UUID meetingId,
            String meetingTitle,
            List<ApprovalRecordView> items
    ) {
    }

    public record PendingApprovalsInboxView(List<PendingApprovalGroupView> groups) {
    }

    public record UpdateNoteBody(String body) {
    }

    public record SubmitNoteApprovalBody(Long expectedVersion) {
    }

    public record DecideBody(
            String decision,
            String comment,
            Long expectedNoteVersion,
            Long expectedApprovalVersion
    ) {
    }

    public record TemplateListView(List<TemplateSummaryView> items) {
    }

    public record TemplateSummaryView(
            UUID id,
            String name,
            String locale,
            int version,
            String status,
            boolean isDefault
    ) {
    }

    public record CreateTemplateBody(String name, String locale) {
    }

    public record TemplateDetailView(
            UUID id,
            String name,
            String locale,
            List<TemplateVersionView> versions,
            UUID publishedVersionId,
            boolean isDefault
    ) {
    }

    public record TemplateVersionView(
            UUID id,
            int versionNumber,
            String status,
            String changelog,
            String updatedAt,
            DesignSchemaView designSchema
    ) {
    }

    public record DesignSchemaView(
            int schemaVersion,
            String pageSize,
            List<DesignComponentView> components
    ) {
    }

    public record DesignComponentView(
            String id,
            String type,
            int order,
            java.util.Map<String, String> props
    ) {
    }

    public record CreateTemplateVersionBody(String changelog) {
    }

    public record SaveTemplateDesignBody(String designSchemaJson, String contentSchemaJson) {
    }

    /**
     * Effective template binding for a note. {@code locked} is false when the binding is only
     * the tenant default suggestion, i.e. the note is not yet pinned to this version.
     */
    public record NoteTemplateLockView(
            UUID templateId,
            String templateName,
            UUID templateVersionId,
            int templateVersionNumber,
            boolean locked,
            DesignSchemaView designSchema
    ) {
    }

    public record LockNoteTemplateBody(UUID templateVersionId) {
    }

    public record UpdateTeamsSettingsBody(boolean autoJoinEnabled) {
    }

    public record UpdateIntelligenceConnectionBody(
            String baseUrl,
            Boolean enabled,
            java.util.Set<String> servedModelIds
    ) {
    }

    public record NanobaseAiConnectionView(
            String productName,
            String mode,
            boolean enabled,
            String endpointHost,
            String baseUrl,
            boolean healthy,
            long latencyMs,
            String statusDetail,
            List<String> servedModelIds,
            String checkedAt
    ) {
    }

    public record TeamsSettingsView(
            boolean tenantConnected,
            String graphAppId,
            String webhookStatus,
            boolean autoJoinEnabled
    ) {
    }

    public record ModelHealthView(
            List<ModelSummaryView> models,
            List<DeploymentSummaryView> deployments,
            RoutingView routing
    ) {
    }

    public record ModelSummaryView(String modelKey, String displayName, boolean enabled, String status) {
    }

    public record DeploymentSummaryView(String deploymentKey, String modelKey, String nodeName, boolean healthy) {
    }

    public record RoutingView(String strategy, List<RoleRoutingView> roles) {
    }

    public record RoleRoutingView(String role, String primaryModel, String fallbackModel) {
    }

    public record AiJobView(
            UUID id,
            UUID meetingId,
            String meetingTitle,
            String status,
            String stage,
            String startedAt,
            String finishedAt
    ) {
    }

    public record OperationsOverviewView(
            int queueDepth,
            int failedJobs,
            List<Object> circuitBreakers,
            List<Object> workers
    ) {
    }

    public record CircuitBreakerView(String name, String state) {
    }

    public record WorkerSummaryView(String name, String status) {
    }

    public record AuditEventView(
            UUID id,
            String action,
            String actorName,
            String resourceLabel,
            String resourceType,
            String resourceId,
            String at
    ) {
    }
}
