package com.nanobaseai.actenora.security.portal;

import com.nanobaseai.actenora.approval.api.ApprovalApi;
import com.nanobaseai.actenora.approval.api.ApprovalDecisionType;
import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.approval.api.ApprovalRequestStatus;
import com.nanobaseai.actenora.approval.api.ApprovalRequestView;
import com.nanobaseai.actenora.aiprocessing.api.AiProcessingApi;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.audit.api.AuditApi;
import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.api.RequiresPermission;
import com.nanobaseai.actenora.identity.api.UserView;
import com.nanobaseai.actenora.identity.api.Permission;
import com.nanobaseai.actenora.meeting.api.MeetingApi;
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
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteDetailResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteUpdateRequest;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNote;
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
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.template.api.MeetingTemplateId;
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
import org.springframework.http.HttpStatus;
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
    private final String recordingBaseUrl;
    private final Optional<GraphObservability> graphObservability;
    private final Optional<TeamsTranscriptPollScheduler> transcriptPollScheduler;

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
            PortalTeamsPreferencesStore teamsPreferencesStore,
            @Value("${actenora.microsoft-graph.client-id:}") String graphClientId,
            @Value("${actenora.portal.recording-base-url:}") String recordingBaseUrl
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
        this.teamsPreferencesStore = Objects.requireNonNull(teamsPreferencesStore, "teamsPreferencesStore");
        this.graphClientId = graphClientId == null ? "" : graphClientId;
        this.recordingBaseUrl = recordingBaseUrl == null ? "" : recordingBaseUrl;
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
                meetingApi.listMeetings(new CursorPageRequest(null, null, null, 5)).items()
        );
        int pendingApprovals = countPendingApprovals(principal.tenantId().value());
        int openActions = ledgerApi.listOpenActionItems(principal.tenantId()).size();
        int overdue = ledgerApi.overdueCommitments(principal.tenantId()).size();
        int runningJobs = resolveRunningJobs(principal.tenantId().value());
        return new DashboardView(pendingApprovals, openActions, overdue, runningJobs, recent);
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
        MeetingListResponse page = meetingApi.listMeetings(
                new CursorPageRequest(parsed, null, cursor, limit == null ? 50 : limit)
        );
        List<MeetingSummaryView> items = toSummaries(page.items());
        if (q != null && !q.isBlank()) {
            String needle = q.trim().toLowerCase(Locale.ROOT);
            items = items.stream()
                    .filter(m -> m.title() != null && m.title().toLowerCase(Locale.ROOT).contains(needle))
                    .toList();
        }
        return new PortalCursorPage<>(items, page.nextCursor());
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
        for (LedgerEvent event : ledgerApi.listEvents(
                TenantSecurityContext.require().tenantId())) {
            if (!meetingId.equals(event.meetingOccurrenceId())) {
                continue;
            }
            if (event.type() == LedgerEventType.DECISION_RECORDED) {
                decisions.add(new DecisionItemView(
                        event.aggregateId(),
                        meetingId,
                        payloadText(event),
                        "APPROVED",
                        List.of(),
                        event.occurredAt().toString()
                ));
            } else if (event.type() == LedgerEventType.COMMITMENT_RECORDED) {
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

        List<MeetingNoteView> notes = new ArrayList<>();
        List<ApprovalRecordView> approvalHistory = new ArrayList<>();
        if (meetingIntelligenceApi.isPresent()) {
            for (MeetingNoteDetailResponse note : meetingIntelligenceApi.get().listNotesForMeeting(meetingId)) {
                notes.add(new MeetingNoteView(
                        note.id(),
                        "SHARED",
                        note.currentVersion() == null ? "" : note.currentVersion().executiveSummary(),
                        note.updatedAt() == null ? null : note.updatedAt().toString(),
                        note.currentVersion() == null ? null : note.currentVersion().createdByUserId()
                ));
                if (note.currentVersion() != null) {
                    approvalApi.findBySubject(
                            principal.tenantId().value(),
                            note.currentVersion().id()
                    ).ifPresent(approvalId ->
                            approvalApi.get(principal.tenantId().value(), approvalId).ifPresent(view ->
                                    approvalHistory.add(toApprovalRecord(view, note.id(), null))));
                }
            }
        }

        List<ActionItemView> actions = ledgerApi.listOpenTasks(principal.tenantId(), meetingId).stream()
                .map(PortalApiController::toActionItemView)
                .toList();

        return new MeetingDetailView(
                summary,
                portalParticipants,
                null,
                null,
                List.of(),
                approvalHistory,
                notes,
                decisions,
                actions,
                List.of(),
                commitments,
                List.of(),
                false,
                resolveRecording(meeting)
        );
    }

    private TenantId principalTenantId() {
        return TenantSecurityContext.require().tenantId();
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
            List<Map<String, Object>> segmentPayload =
                    segments.stream().map(PortalApiController::toTranscriptSegment).toList();
            return new TranscriptView(segmentPayload, speakers);
        } catch (TranscriptDomainException ex) {
            if ("TRANSCRIPT_NOT_FOUND".equals(ex.code())) {
                return new TranscriptView(List.of(), List.of());
            }
            throw ex;
        }
    }

    private static Map<String, Object> toTranscriptSegment(TranscriptSegmentView segment) {
        return Map.of(
                "id", segment.id(),
                "speaker", segment.speaker(),
                "text", segment.text(),
                "startMs", segment.startMs(),
                "endMs", segment.endMs(),
                "markers", segment.markers()
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
                TenantSecurityContext.require().userId()
        );
    }

    @PostMapping("/approvals/{approvalId}/decide")
    @RequiresPermission(Permission.APPROVAL_DECIDE)
    public ApprovalRecordView decideApproval(
            @PathVariable UUID approvalId,
            @RequestBody DecideBody body
    ) {
        AuthenticatedPrincipal principal = require(Permission.APPROVAL_DECIDE);
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
                    event.occurredAt().toString()
            ));
        }
        return page(items, cursor, limit);
    }

    @GetMapping("/actions")
    @RequiresPermission(Permission.MEETING_READ)
    public PortalCursorPage<ActionItemView> listActions(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit,
            HttpServletResponse response
    ) {
        AuthenticatedPrincipal principal = require(Permission.MEETING_READ);
        List<ActionItemView> items = ledgerApi.listOpenActionItems(principal.tenantId()).stream()
                .map(PortalApiController::toActionItemView)
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
        ledgerApi.completeActionItem(tenantId, actionId);
        return toActionItemView(ledgerApi.findActionItem(tenantId, actionId).orElseThrow());
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
        List<AiJobView> items = aiProcessingApi.get().listJobsForTenant(tenantId).stream()
                .map(PortalApiController::toAiJobView)
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
                    workers.workers().stream().map(w -> (Object) w).toList()
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
        List<AuditEventView> items = auditApi.get().listForTenant(tenantId).stream()
                .map(PortalApiController::toAuditEventView)
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
        return new ActionItemView(
                item.id(),
                item.meetingOccurrenceId(),
                item.text(),
                item.status().name(),
                "unknown",
                null,
                List.of()
        );
    }

    private static AiJobView toAiJobView(AiJob job) {
        return new AiJobView(
                job.id(),
                job.meetingOccurrenceId(),
                job.status().name(),
                job.taskType(),
                job.startedAt().map(Instant::toString).orElse(job.queuedAt().toString()),
                job.completedAt().map(Instant::toString).orElse(null)
        );
    }

    private static AuditEventView toAuditEventView(AuditApi.AuditTimelineEntry entry) {
        return new AuditEventView(
                entry.id(),
                entry.action(),
                entry.actorId(),
                entry.resourceType(),
                entry.resourceId().toString(),
                entry.occurredAt().toString()
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

    private RecordingView resolveRecording(MeetingResponse meeting) {
        String url = meeting.joinWebUrl();
        if ((url == null || url.isBlank()) && meeting.teamsMeetingId() != null && !recordingBaseUrl.isBlank()) {
            url = recordingBaseUrl.replace("{meetingId}", meeting.teamsMeetingId());
        }
        if (url == null || url.isBlank()) {
            return null;
        }
        return new RecordingView(url, null, null);
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
        return new ModelHealthView(models, deployments, new RoutingView("prefer-registry", List.of()));
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
                 "MEETING_NOT_FOUND" -> HttpStatus.NOT_FOUND;
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
            List<Object> versions,
            List<ApprovalRecordView> approvalHistory,
            List<MeetingNoteView> notes,
            List<DecisionItemView> decisions,
            List<ActionItemView> actions,
            List<Object> risks,
            List<CommitmentItemView> commitments,
            List<String> qualityFlags,
            boolean partial,
            RecordingView recording
    ) {
    }

    public record RecordingView(String url, String contentType, Long durationMs) {
    }

    public record TranscriptView(List<? extends Object> segments, List<String> speakers) {
    }

    public record MeetingNoteView(
            UUID id,
            String visibility,
            String body,
            String updatedAt,
            UUID authorId
    ) {
    }

    public record DecisionItemView(
            UUID id,
            UUID meetingId,
            String title,
            String status,
            List<Object> evidence,
            String createdAt
    ) {
    }

    public record ActionItemView(
            UUID id,
            UUID meetingId,
            String title,
            String status,
            String ownerDisplayName,
            String dueAt,
            List<Object> evidence
    ) {
    }

    public record CommitmentItemView(
            UUID id,
            UUID meetingId,
            String statement,
            String ownerDisplayName,
            String dueAt,
            String status,
            List<Object> evidence
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

    public record AuditEventView(
            UUID id,
            String action,
            String actor,
            String resourceType,
            String resourceId,
            String at
    ) {
    }
}
