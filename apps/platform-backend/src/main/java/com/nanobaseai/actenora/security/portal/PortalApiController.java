package com.nanobaseai.actenora.security.portal;

import com.nanobaseai.actenora.approval.api.ApprovalApi;
import com.nanobaseai.actenora.approval.api.ApprovalDecisionType;
import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.approval.api.ApprovalRequestStatus;
import com.nanobaseai.actenora.approval.api.ApprovalRequestView;
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
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteUpdateRequest;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNote;
import com.nanobaseai.actenora.operations.api.OperationsApi;
import com.nanobaseai.actenora.operations.application.OperationsViews;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
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
import java.util.List;
import java.util.Locale;
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

    public PortalApiController(
            IdentityApi identityApi,
            MeetingApi meetingApi,
            ContinuityLedgerApi ledgerApi,
            ApprovalApi approvalApi,
            MeetingNoteApprovalService noteApprovalService,
            ObjectProvider<MeetingIntelligenceApi> meetingIntelligenceApi,
            ObjectProvider<OperationsApi> operationsApi
    ) {
        this.identityApi = Objects.requireNonNull(identityApi, "identityApi");
        this.meetingApi = Objects.requireNonNull(meetingApi, "meetingApi");
        this.ledgerApi = Objects.requireNonNull(ledgerApi, "ledgerApi");
        this.approvalApi = Objects.requireNonNull(approvalApi, "approvalApi");
        this.noteApprovalService = Objects.requireNonNull(noteApprovalService, "noteApprovalService");
        this.meetingIntelligenceApi = Optional.ofNullable(meetingIntelligenceApi.getIfAvailable());
        this.operationsApi = Optional.ofNullable(operationsApi.getIfAvailable());
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
        int overdue = ledgerApi.overdueCommitments(principal.tenantId()).size();
        return new DashboardView(0, 0, overdue, 0, recent);
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

        return new MeetingDetailView(
                summary,
                portalParticipants,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                decisions,
                List.of(),
                List.of(),
                commitments,
                List.of(),
                false
        );
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
        markStub(response);
        return new TranscriptView(List.of(), List.of());
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
        require(Permission.MEETING_READ);
        markStub(response);
        return page(List.of(), cursor, limit);
    }

    @PostMapping("/actions/{actionId}/complete")
    @RequiresPermission(Permission.MEETING_WRITE)
    public ActionItemView completeAction(@PathVariable UUID actionId) {
        require(Permission.MEETING_WRITE);
        throw new ActenoraException("ACTION_NOT_FOUND", "Action not found: " + actionId);
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
    @RequiresPermission(Permission.MEETING_READ)
    public TemplateListView listTemplates(HttpServletResponse response) {
        require(Permission.MEETING_READ);
        markStub(response);
        return new TemplateListView(List.of());
    }

    @GetMapping("/teams/settings")
    @RequiresPermission(Permission.TENANT_READ)
    public TeamsSettingsView teamsSettings(HttpServletResponse response) {
        require(Permission.TENANT_READ);
        markStub(response);
        return new TeamsSettingsView(false, "", "not_configured", false);
    }

    @GetMapping("/model-control/health")
    @RequiresPermission(Permission.MODEL_CONTROL)
    public ModelHealthView modelHealth(HttpServletResponse response) {
        require(Permission.MODEL_CONTROL);
        markStub(response);
        return new ModelHealthView(List.of(), List.of(), new RoutingView("prefer-registry", List.of()));
    }

    @GetMapping("/ai-jobs")
    @RequiresPermission(Permission.MEETING_READ)
    public PortalCursorPage<AiJobView> listAiJobs(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit,
            HttpServletResponse response
    ) {
        require(Permission.MEETING_READ);
        markStub(response);
        return page(List.of(), cursor, limit);
    }

    @GetMapping("/operations/overview")
    @RequiresPermission(Permission.OPERATIONS_MANAGE)
    public OperationsOverviewView operationsOverview(HttpServletResponse response) {
        require(Permission.OPERATIONS_MANAGE);
        if (operationsApi.isPresent()) {
            OperationsViews.QueueDashboardView dashboard = operationsApi.get().queueDashboard();
            OperationsViews.WorkerHealthView workers = operationsApi.get().workerHealth();
            return new OperationsOverviewView(
                    (int) dashboard.aiQueueDepth(),
                    (int) dashboard.dlqDepth(),
                    List.of(),
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
        markStub(response);
        return page(List.of(), cursor, limit);
    }

    private static void markStub(HttpServletResponse response) {
        if (response != null) {
            response.setHeader(COMPOSITION_STUB_HEADER, "stub");
        }
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
            case "ACTION_NOT_FOUND", "INTELLIGENCE_RESOURCE_NOT_FOUND", "MEETING_NOTE_NOT_FOUND"
                    -> HttpStatus.NOT_FOUND;
            case "NOTE_UPDATE_UNAVAILABLE" -> HttpStatus.NOT_IMPLEMENTED;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
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
            boolean partial
    ) {
    }

    public record TranscriptView(List<Object> segments, List<String> speakers) {
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

    public record UpdateNoteBody(String body) {
    }

    public record DecideBody(
            String decision,
            String comment,
            Long expectedNoteVersion,
            Long expectedApprovalVersion
    ) {
    }

    public record TemplateListView(List<Object> items) {
    }

    public record TeamsSettingsView(
            boolean tenantConnected,
            String graphAppId,
            String webhookStatus,
            boolean autoJoinEnabled
    ) {
    }

    public record ModelHealthView(
            List<Object> models,
            List<Object> deployments,
            RoutingView routing
    ) {
    }

    public record RoutingView(String strategy, List<Object> roles) {
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
