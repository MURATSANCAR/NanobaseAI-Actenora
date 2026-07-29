package com.nanobaseai.actenora.security.portal;

import com.nanobaseai.actenora.approval.api.ApprovalApi;
import com.nanobaseai.actenora.approval.api.ApprovalRequestStatus;
import com.nanobaseai.actenora.approval.api.ApprovalRequestView;
import com.nanobaseai.actenora.approval.api.ApprovalSubjectType;
import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.api.Permission;
import com.nanobaseai.actenora.identity.api.UserView;
import com.nanobaseai.actenora.identity.api.RequiresPermission;
import com.nanobaseai.actenora.meeting.api.MeetingApi;
import com.nanobaseai.actenora.meeting.api.dto.MeetingResponse;
import com.nanobaseai.actenora.meeting.api.dto.ParticipantResponse;
import com.nanobaseai.actenora.meetingintelligence.api.MeetingIntelligenceApi;
import com.nanobaseai.actenora.meetingintelligence.api.dto.ActionItemResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteDetailResponse;
import com.nanobaseai.actenora.meetingintelligence.api.ledger.ContinuityLedgerApi;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingKnowledgeStorePort;
import com.nanobaseai.actenora.meetingintelligence.domain.knowledge.KnowledgeSearchHit;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.CommitmentConfirmation;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContradictionCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContradictionStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.DecisionHistoryEntry;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.MeetingBrief;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.projection.LedgerProjectionState;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNoteStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.CommitmentConfirmationStatus;
import com.nanobaseai.actenora.microsoftconnection.api.MicrosoftConnectionApi;
import com.nanobaseai.actenora.microsoftconnection.application.model.OutlookDraftRequest;
import com.nanobaseai.actenora.microsoftconnection.application.model.OutlookDraftResult;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.graph.GraphApiException;
import com.nanobaseai.actenora.security.aiprocessing.NanobaseAiBrandSanitizer;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import com.nanobaseai.actenora.transcript.api.TranscriptApi;
import com.nanobaseai.actenora.transcript.api.dto.TranscriptSegmentView;
import com.nanobaseai.actenora.transcript.domain.TranscriptDomainException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Portal read/write composition for meeting preparation, personal work, search,
 * evidence-grounded Q&A, and Outlook draft handoff.
 */
@RestController
@RequestMapping("/api/v1/portal")
public final class PortalProductivityController {

    private final IdentityApi identityApi;
    private final MeetingApi meetingApi;
    private final ContinuityLedgerApi ledgerApi;
    private final ApprovalApi approvalApi;
    private final Optional<MeetingIntelligenceApi> meetingIntelligenceApi;
    private final Optional<MeetingKnowledgeStorePort> knowledgeStore;
    private final Optional<TranscriptApi> transcriptApi;
    private final Optional<MicrosoftConnectionApi> microsoftConnectionApi;
    private final MeetingQuestionService questionService;
    private final OutlookDraftComposer draftComposer;
    private final Clock clock;
    private final ZoneId businessZone;
    private final LocalTime dateOnlyDeadlineTime;
    private final Duration upcomingWindow;
    private final int recentCommitmentLimit;
    private final int questionEvidenceLimit;
    private final int defaultGlobalSearchLimit;
    private final int globalSearchLimit;
    private final List<String> agendaSourceOrder;

    @Autowired
    public PortalProductivityController(
            IdentityApi identityApi,
            MeetingApi meetingApi,
            ContinuityLedgerApi ledgerApi,
            ApprovalApi approvalApi,
            ObjectProvider<MeetingIntelligenceApi> meetingIntelligenceApi,
            ObjectProvider<MeetingKnowledgeStorePort> knowledgeStore,
            ObjectProvider<TranscriptApi> transcriptApi,
            ObjectProvider<MicrosoftConnectionApi> microsoftConnectionApi,
            MeetingQuestionService questionService,
            OutlookDraftComposer draftComposer,
            @Value("${actenora.portal.business-zone}") String businessZone,
            @Value("${actenora.portal.my-work.date-only-deadline-time}") LocalTime dateOnlyDeadlineTime,
            @Value("${actenora.portal.my-work.upcoming-window}") Duration upcomingWindow,
            @Value("${actenora.portal.my-work.recent-commitment-limit}") int recentCommitmentLimit,
            @Value("${actenora.portal.meeting-question.evidence-limit}") int questionEvidenceLimit,
            @Value("${actenora.portal.global-search.default-limit}") int defaultGlobalSearchLimit,
            @Value("${actenora.portal.global-search.max-limit}") int globalSearchLimit,
            @Value("${actenora.portal.meeting-prep.agenda-source-order}") List<String> agendaSourceOrder
    ) {
        this(
                identityApi,
                meetingApi,
                ledgerApi,
                approvalApi,
                Optional.ofNullable(meetingIntelligenceApi.getIfAvailable()),
                Optional.ofNullable(knowledgeStore.getIfAvailable()),
                Optional.ofNullable(transcriptApi.getIfAvailable()),
                Optional.ofNullable(microsoftConnectionApi.getIfAvailable()),
                questionService,
                draftComposer,
                Clock.systemUTC(),
                ZoneId.of(businessZone),
                dateOnlyDeadlineTime,
                upcomingWindow,
                recentCommitmentLimit,
                questionEvidenceLimit,
                defaultGlobalSearchLimit,
                globalSearchLimit,
                agendaSourceOrder
        );
    }

    PortalProductivityController(
            IdentityApi identityApi,
            MeetingApi meetingApi,
            ContinuityLedgerApi ledgerApi,
            ApprovalApi approvalApi,
            Optional<MeetingIntelligenceApi> meetingIntelligenceApi,
            Optional<MeetingKnowledgeStorePort> knowledgeStore,
            Optional<TranscriptApi> transcriptApi,
            Optional<MicrosoftConnectionApi> microsoftConnectionApi,
            MeetingQuestionService questionService,
            OutlookDraftComposer draftComposer,
            Clock clock,
            ZoneId businessZone,
            LocalTime dateOnlyDeadlineTime,
            Duration upcomingWindow,
            int recentCommitmentLimit,
            int questionEvidenceLimit,
            int defaultGlobalSearchLimit,
            int globalSearchLimit,
            List<String> agendaSourceOrder
    ) {
        this.identityApi = Objects.requireNonNull(identityApi, "identityApi");
        this.meetingApi = Objects.requireNonNull(meetingApi, "meetingApi");
        this.ledgerApi = Objects.requireNonNull(ledgerApi, "ledgerApi");
        this.approvalApi = Objects.requireNonNull(approvalApi, "approvalApi");
        this.meetingIntelligenceApi = Objects.requireNonNull(meetingIntelligenceApi, "meetingIntelligenceApi");
        this.knowledgeStore = Objects.requireNonNull(knowledgeStore, "knowledgeStore");
        this.transcriptApi = Objects.requireNonNull(transcriptApi, "transcriptApi");
        this.microsoftConnectionApi = Objects.requireNonNull(microsoftConnectionApi, "microsoftConnectionApi");
        this.questionService = Objects.requireNonNull(questionService, "questionService");
        this.draftComposer = Objects.requireNonNull(draftComposer, "draftComposer");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.businessZone = Objects.requireNonNull(businessZone, "businessZone");
        this.dateOnlyDeadlineTime = Objects.requireNonNull(dateOnlyDeadlineTime, "dateOnlyDeadlineTime");
        this.upcomingWindow = positive(upcomingWindow, "upcomingWindow");
        this.recentCommitmentLimit = positive(recentCommitmentLimit, "recentCommitmentLimit");
        this.questionEvidenceLimit = bounded(questionEvidenceLimit, 1, 100, "questionEvidenceLimit");
        this.defaultGlobalSearchLimit = bounded(
                defaultGlobalSearchLimit,
                1,
                globalSearchLimit,
                "defaultGlobalSearchLimit");
        this.globalSearchLimit = bounded(globalSearchLimit, 1, 100, "globalSearchLimit");
        this.agendaSourceOrder = List.copyOf(Objects.requireNonNull(agendaSourceOrder, "agendaSourceOrder"))
                .stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        if (this.agendaSourceOrder.isEmpty()) {
            throw new IllegalArgumentException("agendaSourceOrder must not be empty");
        }
    }

    @GetMapping("/meetings/{meetingId}/brief")
    @RequiresPermission(Permission.MEETING_READ)
    public MeetingPrepView meetingPrep(@PathVariable UUID meetingId) {
        AuthenticatedPrincipal principal = require(Permission.MEETING_READ);
        meetingApi.getMeeting(meetingId);
        MeetingBrief brief = ledgerApi.generateBrief(principal.tenantId(), meetingId);
        Set<UUID> relevantOccurrences = new LinkedHashSet<>();
        relevantOccurrences.add(meetingId);
        brief.previousOccurrenceId().ifPresent(relevantOccurrences::add);

        List<ContradictionView> contradictions = ledgerApi.listContradictions(principal.tenantId()).stream()
                .filter(candidate -> relevantOccurrences.contains(candidate.meetingOccurrenceId()))
                .filter(candidate -> candidate.status() != ContradictionStatus.REJECTED)
                .sorted(Comparator.comparing(ContradictionCandidate::createdAt).reversed())
                .map(ContradictionView::from)
                .toList();

        Map<String, List<AgendaItemView>> agendaBySource = new LinkedHashMap<>();
        brief.unresolvedQuestions().forEach(item -> putAgenda(agendaBySource, AgendaItemView.from(item)));
        brief.openRisks().forEach(item -> putAgenda(agendaBySource, AgendaItemView.from(item)));
        brief.openTasks().forEach(item -> putAgenda(agendaBySource, AgendaItemView.from(item)));
        brief.overdueCommitments().forEach(item -> putAgenda(agendaBySource, AgendaItemView.from(item)));
        List<AgendaItemView> agenda = agendaSourceOrder.stream()
                .flatMap(source -> agendaBySource.getOrDefault(source, List.of()).stream())
                .toList();

        return new MeetingPrepView(
                brief.briefId(),
                brief.targetOccurrenceId(),
                brief.previousOccurrenceId().orElse(null),
                brief.meetingSeriesId().orElse(null),
                brief.businessContextId().orElse(null),
                brief.activeDecisions().stream().map(DecisionView::from).toList(),
                brief.openTasks().stream().map(CarryOverView::from).toList(),
                brief.openRisks().stream().map(CarryOverView::from).toList(),
                brief.unresolvedQuestions().stream().map(CarryOverView::from).toList(),
                brief.overdueCommitments().stream().map(CommitmentView::from).toList(),
                contradictions,
                List.copyOf(agenda),
                brief.generatedAt()
        );
    }

    @GetMapping("/my-work")
    @RequiresPermission(Permission.MEETING_READ)
    public MyWorkView myWork() {
        AuthenticatedPrincipal principal = require(Permission.MEETING_READ);
        Map<UUID, UserView> users = identityApi.listUsers(principal.tenantId()).stream()
                .collect(Collectors.toMap(UserView::id, Function.identity()));
        Map<String, UUID> ownerIndex = buildOwnerIndex(users.values());
        Map<UUID, ActionItemResponse> richActions = meetingIntelligenceApi
                .map(MeetingIntelligenceApi::listActionItems)
                .orElseGet(List::of)
                .stream()
                .collect(Collectors.toMap(ActionItemResponse::id, Function.identity(), (left, right) -> right));

        Instant now = clock.instant();
        Instant upcomingEnd = now.plus(upcomingWindow);
        LocalDate today = LocalDate.ofInstant(now, businessZone);
        List<WorkActionView> assigned = new ArrayList<>();
        List<WorkActionView> dueSoon = new ArrayList<>();
        List<WorkActionView> overdue = new ArrayList<>();

        for (LedgerProjectionState.TrackedActionItem ledgerAction :
                ledgerApi.listOpenActionItems(principal.tenantId())) {
            ActionItemResponse rich = richActions.get(ledgerAction.id());
            if (rich == null || !isMine(rich.owner(), principal.userId(), ownerIndex)) {
                continue;
            }
            WorkActionView view = WorkActionView.from(
                    ledgerAction,
                    rich,
                    businessZone,
                    dateOnlyDeadlineTime);
            assigned.add(view);
            Instant due = effectiveDueAt(rich, businessZone, dateOnlyDeadlineTime);
            if (due != null && due.isBefore(now)) {
                overdue.add(view);
            } else if (due != null && !due.isAfter(upcomingEnd)) {
                dueSoon.add(view);
            }
        }

        Comparator<WorkActionView> actionOrder = Comparator
                .comparing(WorkActionView::dueAt, Comparator.nullsLast(String::compareTo))
                .thenComparing(WorkActionView::title);
        assigned.sort(actionOrder);
        dueSoon.sort(actionOrder);
        overdue.sort(actionOrder);

        List<CommitmentView> commitments = ledgerApi.listCommitments(principal.tenantId()).stream()
                .filter(commitment -> commitment.owner()
                        .map(owner -> isMine(owner, principal.userId(), ownerIndex))
                        .orElse(false))
                .filter(commitment -> commitment.status() != CommitmentConfirmationStatus.REJECTED)
                .sorted(Comparator.comparing(CommitmentConfirmation::updatedAt).reversed())
                .limit(recentCommitmentLimit)
                .map(CommitmentView::from)
                .toList();

        List<PendingApprovalView> approvals = principal.hasPermission(Permission.APPROVAL_DECIDE.code())
                ? approvalApi.listForTenant(principal.tenantId().value()).stream()
                        .filter(view -> view.status() == ApprovalRequestStatus.PENDING
                                || view.status() == ApprovalRequestStatus.CHANGES_REQUESTED)
                        .sorted(Comparator.comparing(ApprovalRequestView::updatedAt).reversed())
                        .map(PendingApprovalView::from)
                        .toList()
                : List.of();

        return new MyWorkView(
                List.copyOf(assigned),
                List.copyOf(dueSoon),
                List.copyOf(overdue),
                approvals,
                commitments,
                today.toString(),
                upcomingEnd.toString()
        );
    }

    @PostMapping("/actions/{actionId}/disputes")
    @RequiresPermission(Permission.MEETING_WRITE)
    public ActionRequestView disputeAction(
            @PathVariable UUID actionId,
            @RequestBody ActionDisputeBody body
    ) {
        AuthenticatedPrincipal principal = require(Permission.MEETING_WRITE);
        LedgerProjectionState.TrackedActionItem action = requireAction(principal, actionId);
        String proposed = body.proposedTitle() == null || body.proposedTitle().isBlank()
                ? action.text()
                : body.proposedTitle().trim();
        UUID id = approvalApi.raiseDispute(
                principal.tenantId().value(),
                actionId,
                ApprovalSubjectType.ACTION_ITEM,
                principal.userId().toString(),
                proposed,
                requireText(body.reason(), "reason")
        );
        return new ActionRequestView(id, ApprovalSubjectType.ACTION_ITEM.name());
    }

    @PostMapping("/actions/{actionId}/due-date-change-requests")
    @RequiresPermission(Permission.MEETING_WRITE)
    public ActionRequestView requestDueDateChange(
            @PathVariable UUID actionId,
            @RequestBody DueDateChangeBody body
    ) {
        AuthenticatedPrincipal principal = require(Permission.MEETING_WRITE);
        requireAction(principal, actionId);
        LocalDate requestedDueDate = Objects.requireNonNull(body.requestedDueDate(), "requestedDueDate");
        UUID id = approvalApi.raiseDispute(
                principal.tenantId().value(),
                actionId,
                ApprovalSubjectType.ACTION_ITEM_DUE_DATE,
                principal.userId().toString(),
                requestedDueDate.toString(),
                requireText(body.reason(), "reason")
        );
        return new ActionRequestView(id, ApprovalSubjectType.ACTION_ITEM_DUE_DATE.name());
    }

    @GetMapping("/search")
    @RequiresPermission(Permission.MEETING_READ)
    public GlobalSearchView search(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", required = false) Integer requestedLimit
    ) {
        AuthenticatedPrincipal principal = require(Permission.MEETING_READ);
        String normalized = requireText(query, "q");
        int limit = Math.min(
                requestedLimit == null ? defaultGlobalSearchLimit : Math.max(requestedLimit, 1),
                globalSearchLimit);
        List<GlobalSearchHitView> hits = knowledgeStore
                .map(store -> store.searchFts(principal.tenantId(), normalized, limit))
                .orElseGet(List::of)
                .stream()
                .map(GlobalSearchHitView::from)
                .toList();
        return new GlobalSearchView(normalized, hits);
    }

    @PostMapping("/meetings/{meetingId}/questions")
    @RequiresPermission(Permission.MEETING_READ)
    public MeetingQuestionView askMeeting(
            @PathVariable UUID meetingId,
            @RequestBody MeetingQuestionBody body
    ) {
        AuthenticatedPrincipal principal = require(Permission.MEETING_READ);
        meetingApi.getMeeting(meetingId);
        TranscriptApi transcripts = transcriptApi.orElseThrow(() -> new ActenoraException(
                "TRANSCRIPT_QUERY_UNAVAILABLE",
                "Transcript query is unavailable"));
        List<TranscriptSegmentView> evidence;
        try {
            evidence = transcripts.searchSegmentsForMeeting(
                    principal.tenantId(),
                    meetingId,
                    requireText(body.question(), "question"),
                    questionEvidenceLimit
            );
        } catch (TranscriptDomainException ex) {
            if (!"TRANSCRIPT_NOT_FOUND".equals(ex.code())) {
                throw ex;
            }
            evidence = List.of();
        }
        MeetingQuestionService.Answer answer = questionService.answer(body.question(), evidence);
        Map<String, TranscriptSegmentView> byId = evidence.stream()
                .collect(Collectors.toMap(segment -> segment.id().toString(), Function.identity()));
        List<QuestionCitationView> citations = answer.citationSegmentIds().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(QuestionCitationView::from)
                .toList();
        return new MeetingQuestionView(
                answer.status(),
                answer.text(),
                citations,
                answer.modelVersion(),
                answer.inputTokens(),
                answer.outputTokens()
        );
    }

    @PostMapping("/meetings/{meetingId}/notes/{noteId}/outlook-draft")
    @RequiresPermission(Permission.MEETING_WRITE)
    public OutlookDraftView createOutlookDraft(
            @PathVariable UUID meetingId,
            @PathVariable UUID noteId
    ) {
        AuthenticatedPrincipal principal = require(Permission.MEETING_WRITE);
        MeetingResponse meeting = meetingApi.getMeeting(meetingId);
        MeetingIntelligenceApi intelligence = meetingIntelligenceApi.orElseThrow(() ->
                unavailable("MEETING_INTELLIGENCE_UNAVAILABLE"));
        MicrosoftConnectionApi microsoft = microsoftConnectionApi.orElseThrow(() ->
                unavailable("MICROSOFT_CONNECTION_UNAVAILABLE"));
        MeetingNoteDetailResponse note = intelligence.getNoteDetail(noteId);
        if (!meetingId.equals(note.meetingOccurrenceId())) {
            throw new ActenoraException("NOTE_MEETING_MISMATCH", "Note does not belong to this meeting");
        }
        if (note.currentVersion().approvalStatus() != MeetingNoteStatus.APPROVED) {
            throw new ActenoraException(
                    "OUTLOOK_DRAFT_REQUIRES_APPROVED_NOTE",
                    "Outlook draft requires a human-approved note version");
        }
        List<String> recipients = meetingApi.listParticipants(meetingId).stream()
                .map(ParticipantResponse::email)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(email -> !email.isBlank())
                .filter(email -> !email.equalsIgnoreCase(principal.email()))
                .distinct()
                .toList();
        OutlookDraftComposer.ComposedDraft content = draftComposer.compose(meeting, note);
        String idempotencyKey = UUID.nameUUIDFromBytes(
                (principal.tenantId().value() + ":" + note.currentVersion().id() + ":" + principal.email())
                        .getBytes(StandardCharsets.UTF_8)
        ).toString();
        OutlookDraftResult result = microsoft.createOutlookDraft(
                principal.tenantId().value(),
                new OutlookDraftRequest(
                        principal.email(),
                        content.subject(),
                        content.bodyHtml(),
                        recipients,
                        idempotencyKey
                )
        );
        return new OutlookDraftView(
                result.providerMessageId(),
                result.webLink(),
                result.reused(),
                recipients.size()
        );
    }

    private LedgerProjectionState.TrackedActionItem requireAction(
            AuthenticatedPrincipal principal,
            UUID actionId
    ) {
        return ledgerApi.findActionItem(principal.tenantId(), actionId)
                .orElseThrow(() -> new ActenoraException("ACTION_NOT_FOUND", "Action not found: " + actionId));
    }

    private AuthenticatedPrincipal require(Permission permission) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, permission);
        return principal;
    }

    private static Map<String, UUID> buildOwnerIndex(Iterable<UserView> users) {
        Map<String, UUID> index = new LinkedHashMap<>();
        for (UserView user : users) {
            putAlias(index, user.email(), user.id());
            putAlias(index, user.displayName(), user.id());
            putAlias(index, user.entraObjectId(), user.id());
            putAlias(index, user.id().toString(), user.id());
        }
        return index;
    }

    private static void putAlias(Map<String, UUID> index, String alias, UUID userId) {
        if (alias != null && !alias.isBlank()) {
            index.putIfAbsent(normalize(alias), userId);
        }
    }

    private static void putAgenda(
            Map<String, List<AgendaItemView>> agendaBySource,
            AgendaItemView item
    ) {
        agendaBySource.computeIfAbsent(item.sourceType(), ignored -> new ArrayList<>()).add(item);
    }

    private static boolean isMine(String owner, UUID userId, Map<String, UUID> ownerIndex) {
        return owner != null && userId.equals(ownerIndex.get(normalize(owner)));
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static Instant effectiveDueAt(
            ActionItemResponse action,
            ZoneId zoneId,
            LocalTime dateOnlyDeadlineTime
    ) {
        if (action.dueAt() != null) {
            return action.dueAt();
        }
        return action.dueDate() == null
                ? null
                : action.dueDate().atTime(dateOnlyDeadlineTime).atZone(zoneId).toInstant();
    }

    private static ActenoraException unavailable(String code) {
        return new ActenoraException(code, code);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ActenoraException("INVALID_" + field.toUpperCase(Locale.ROOT), field + " is required");
        }
        return value.trim();
    }

    private static int positive(int value, String field) {
        if (value < 1) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static int bounded(int value, int minimum, int maximum, String field) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(field + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    @ExceptionHandler(ActenoraException.class)
    public ResponseEntity<ProblemDetail> handle(ActenoraException ex) {
        HttpStatus status;
        if (ex instanceof GraphApiException graph) {
            status = graph.statusCode() >= 400
                    ? HttpStatus.resolve(graph.statusCode())
                    : HttpStatus.SERVICE_UNAVAILABLE;
            if (status == null) {
                status = HttpStatus.BAD_GATEWAY;
            }
        } else {
            status = switch (ex.code()) {
                case "ACTION_NOT_FOUND",
                     "GRAPH_NOT_FOUND",
                     "INTELLIGENCE_RESOURCE_NOT_FOUND",
                     "MEETING_NOTE_NOT_FOUND",
                     "MEETING_NOT_FOUND" -> HttpStatus.NOT_FOUND;
                case "UNAUTHORIZED_MEETING_ACCESS" -> HttpStatus.FORBIDDEN;
                case "MEETING_QUESTION_RUNTIME_UNAVAILABLE",
                     "MEETING_INTELLIGENCE_UNAVAILABLE",
                     "MICROSOFT_CONNECTION_UNAVAILABLE",
                     "TRANSCRIPT_QUERY_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
                case "GRAPH_RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
                default -> HttpStatus.UNPROCESSABLE_ENTITY;
            };
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status,
                NanobaseAiBrandSanitizer.sanitize(ex.getMessage()));
        problem.setTitle(ex.code());
        problem.setProperty("code", ex.code());
        return ResponseEntity.status(status).body(problem);
    }

    public record MeetingPrepView(
            UUID briefId,
            UUID targetMeetingId,
            UUID previousMeetingId,
            UUID meetingSeriesId,
            UUID businessContextId,
            List<DecisionView> previousDecisions,
            List<CarryOverView> openActions,
            List<CarryOverView> openRisks,
            List<CarryOverView> openQuestions,
            List<CommitmentView> overdueCommitments,
            List<ContradictionView> contradictions,
            List<AgendaItemView> suggestedAgenda,
            Instant generatedAt
    ) {
    }

    public record DecisionView(
            UUID id,
            UUID meetingId,
            String text,
            Instant recordedAt
    ) {
        static DecisionView from(DecisionHistoryEntry item) {
            return new DecisionView(
                    item.decisionId(),
                    item.meetingOccurrenceId(),
                    item.text(),
                    item.recordedAt());
        }
    }

    public record CarryOverView(
            UUID id,
            String kind,
            String text,
            UUID sourceMeetingId
    ) {
        static CarryOverView from(MeetingBrief.CarryOverItem item) {
            return new CarryOverView(item.itemId(), item.kind(), item.text(), item.sourceOccurrenceId());
        }
    }

    public record CommitmentView(
            UUID id,
            UUID meetingId,
            String text,
            String owner,
            String dueDate,
            String status,
            boolean overdue,
            Instant updatedAt
    ) {
        static CommitmentView from(CommitmentConfirmation item) {
            return new CommitmentView(
                    item.commitmentId(),
                    item.meetingOccurrenceId(),
                    item.text(),
                    item.owner().orElse(null),
                    item.dueDate().map(LocalDate::toString).orElse(null),
                    item.status().name(),
                    item.overdue(),
                    item.updatedAt());
        }
    }

    public record ContradictionView(
            UUID id,
            UUID meetingId,
            UUID leftDecisionId,
            UUID rightDecisionId,
            String reason,
            String confidence,
            String status
    ) {
        static ContradictionView from(ContradictionCandidate item) {
            return new ContradictionView(
                    item.id(),
                    item.meetingOccurrenceId(),
                    item.leftDecisionId(),
                    item.rightDecisionId(),
                    item.reason(),
                    item.confidence().toPlainString(),
                    item.status().name());
        }
    }

    public record AgendaItemView(
            UUID id,
            String sourceType,
            String text,
            UUID sourceMeetingId
    ) {
        static AgendaItemView from(MeetingBrief.CarryOverItem item) {
            return new AgendaItemView(item.itemId(), item.kind(), item.text(), item.sourceOccurrenceId());
        }

        static AgendaItemView from(CommitmentConfirmation item) {
            return new AgendaItemView(
                    item.commitmentId(),
                    "COMMITMENT",
                    item.text(),
                    item.meetingOccurrenceId());
        }
    }

    public record MyWorkView(
            List<WorkActionView> assignedActions,
            List<WorkActionView> dueSoonActions,
            List<WorkActionView> overdueActions,
            List<PendingApprovalView> pendingApprovals,
            List<CommitmentView> recentCommitments,
            String today,
            String upcomingUntil
    ) {
    }

    public record WorkActionView(
            UUID id,
            UUID meetingId,
            UUID noteId,
            String title,
            String status,
            String owner,
            String dueAt,
            String priority,
            String ownerType,
            long version
    ) {
        static WorkActionView from(
                LedgerProjectionState.TrackedActionItem ledger,
                ActionItemResponse rich,
                ZoneId zoneId,
                LocalTime dateOnlyDeadlineTime
        ) {
            Instant due = effectiveDueAt(rich, zoneId, dateOnlyDeadlineTime);
            return new WorkActionView(
                    ledger.id(),
                    ledger.meetingOccurrenceId(),
                    ledger.noteId(),
                    rich.text(),
                    rich.status().name(),
                    rich.owner(),
                    due == null ? null : due.toString(),
                    rich.priority(),
                    rich.ownerType(),
                    rich.version()
            );
        }
    }

    public record PendingApprovalView(
            UUID id,
            String subjectType,
            UUID subjectId,
            String status,
            long version,
            Instant updatedAt,
            Instant expiresAt
    ) {
        static PendingApprovalView from(ApprovalRequestView item) {
            return new PendingApprovalView(
                    item.id().value(),
                    item.subjectType().name(),
                    item.subjectId(),
                    item.status().name(),
                    item.version(),
                    item.updatedAt(),
                    item.expiresAt());
        }
    }

    public record ActionDisputeBody(String reason, String proposedTitle) {
    }

    public record DueDateChangeBody(LocalDate requestedDueDate, String reason) {
    }

    public record ActionRequestView(UUID requestId, String requestType) {
    }

    public record GlobalSearchView(String query, List<GlobalSearchHitView> items) {
    }

    public record GlobalSearchHitView(
            UUID id,
            UUID meetingId,
            UUID sourceItemId,
            String kind,
            String content,
            double score,
            String href
    ) {
        static GlobalSearchHitView from(KnowledgeSearchHit hit) {
            return new GlobalSearchHitView(
                    hit.itemId(),
                    hit.meetingOccurrenceId(),
                    hit.sourceItemId(),
                    hit.itemKind().name(),
                    hit.content(),
                    hit.score(),
                    "/meetings/" + hit.meetingOccurrenceId()
            );
        }
    }

    public record MeetingQuestionBody(String question) {
    }

    public record MeetingQuestionView(
            String status,
            String answer,
            List<QuestionCitationView> citations,
            String modelVersion,
            long inputTokens,
            long outputTokens
    ) {
    }

    public record QuestionCitationView(
            UUID segmentId,
            String speaker,
            String quote,
            long startMs,
            long endMs
    ) {
        static QuestionCitationView from(TranscriptSegmentView segment) {
            return new QuestionCitationView(
                    segment.id(),
                    segment.speaker(),
                    segment.text(),
                    segment.startMs(),
                    segment.endMs());
        }
    }

    public record OutlookDraftView(
            String providerMessageId,
            String webLink,
            boolean reused,
            int recipientCount
    ) {
    }
}
