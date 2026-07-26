package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.api.RequiresPermission;
import com.nanobaseai.actenora.identity.api.Permission;
import com.nanobaseai.actenora.meetingintelligence.api.ledger.ContinuityLedgerApi;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.CommitmentConfirmation;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContinuityProjection;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContinuityRelationSuggestion;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContinuitySuggestionStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContradictionCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContradictionStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.MeetingBrief;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.event.LedgerEvent;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.event.LedgerEventType;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.projection.LedgerProjectionState;
import com.nanobaseai.actenora.meetingintelligence.domain.model.CommitmentConfirmationStatus;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Auth-bound Continuity Ledger HTTP surface (FAZ 21–22 ops + FAZ 24 rebuild + FAZ 25 commitments).
 */
@RestController
@RequestMapping("/api/v1/continuity-ledger")
public class ContinuityLedgerAuthController {

    private final ContinuityLedgerApi ledgerApi;
    private final IdentityApi identityApi;

    public ContinuityLedgerAuthController(ContinuityLedgerApi ledgerApi, IdentityApi identityApi) {
        this.ledgerApi = Objects.requireNonNull(ledgerApi, "ledgerApi");
        this.identityApi = Objects.requireNonNull(identityApi, "identityApi");
    }

    @GetMapping("/events")
    @RequiresPermission(Permission.MEETING_READ)
    public List<LedgerEventView> listEvents() {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_READ);
        return ledgerApi.listEvents(principal.tenantId()).stream().map(LedgerEventView::from).toList();
    }

    @GetMapping("/suggestions")
    @RequiresPermission(Permission.MEETING_READ)
    public List<SuggestionView> listSuggestions() {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_READ);
        return ledgerApi.listSuggestions(principal.tenantId()).stream().map(SuggestionView::from).toList();
    }

    @GetMapping("/contradictions")
    @RequiresPermission(Permission.MEETING_READ)
    public List<ContradictionView> listContradictions() {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_READ);
        return ledgerApi.listContradictions(principal.tenantId()).stream().map(ContradictionView::from).toList();
    }

    @PostMapping("/suggestions")
    @RequiresPermission(Permission.MEETING_WRITE)
    public SuggestionView recordSuggestion(@RequestBody RecordSuggestionBody body) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_WRITE);
        Objects.requireNonNull(body, "body");
        ContinuityRelationSuggestion suggestion = ledgerApi.recordRelationSuggestion(
                principal.tenantId(),
                body.sourceOccurrenceId(),
                body.targetOccurrenceId(),
                ContinuityRelationSuggestion.ProposedRelation.valueOf(body.proposedRelation()),
                body.confidence() == null ? new BigDecimal("0.80") : body.confidence(),
                body.reason()
        );
        return SuggestionView.from(suggestion);
    }

    @PostMapping("/suggestions/{suggestionId}/accept")
    @RequiresPermission(Permission.MEETING_WRITE)
    public SuggestionView acceptSuggestion(@PathVariable UUID suggestionId) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_WRITE);
        try {
            return SuggestionView.from(ledgerApi.approveRelationSuggestion(
                    principal.tenantId(),
                    suggestionId,
                    principal.userId().toString()
            ));
        } catch (IllegalArgumentException ex) {
            throw new ActenoraException("INTELLIGENCE_RESOURCE_NOT_FOUND", ex.getMessage());
        }
    }

    @PostMapping("/suggestions/{suggestionId}/reject")
    @RequiresPermission(Permission.MEETING_WRITE)
    public SuggestionView rejectSuggestion(@PathVariable UUID suggestionId) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_WRITE);
        try {
            return SuggestionView.from(ledgerApi.rejectRelationSuggestion(
                    principal.tenantId(),
                    suggestionId,
                    principal.userId().toString()
            ));
        } catch (IllegalArgumentException ex) {
            throw new ActenoraException("INTELLIGENCE_RESOURCE_NOT_FOUND", ex.getMessage());
        }
    }

    @PostMapping("/contradictions")
    @RequiresPermission(Permission.MEETING_WRITE)
    public ContradictionView proposeContradiction(@RequestBody ProposeContradictionBody body) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_WRITE);
        Objects.requireNonNull(body, "body");
        ContradictionCandidate candidate = ledgerApi.proposeContradiction(
                principal.tenantId(),
                body.meetingOccurrenceId(),
                body.leftDecisionId(),
                body.rightDecisionId(),
                body.reason(),
                body.confidence() == null ? new BigDecimal("0.90") : body.confidence()
        );
        return ContradictionView.from(candidate);
    }

    @PostMapping("/contradictions/{contradictionId}/confirm")
    @RequiresPermission(Permission.MEETING_WRITE)
    public ContradictionView confirmContradiction(@PathVariable UUID contradictionId) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_WRITE);
        try {
            return ContradictionView.from(ledgerApi.confirmContradiction(
                    principal.tenantId(),
                    contradictionId,
                    principal.userId().toString()
            ));
        } catch (IllegalArgumentException ex) {
            throw new ActenoraException("INTELLIGENCE_RESOURCE_NOT_FOUND", ex.getMessage());
        }
    }

    @PostMapping("/contradictions/{contradictionId}/reject")
    @RequiresPermission(Permission.MEETING_WRITE)
    public ContradictionView rejectContradiction(@PathVariable UUID contradictionId) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_WRITE);
        try {
            return ContradictionView.from(ledgerApi.rejectContradiction(
                    principal.tenantId(),
                    contradictionId,
                    principal.userId().toString()
            ));
        } catch (IllegalArgumentException ex) {
            throw new ActenoraException("INTELLIGENCE_RESOURCE_NOT_FOUND", ex.getMessage());
        }
    }

    @PostMapping("/commitments")
    @RequiresPermission(Permission.MEETING_WRITE)
    public CommitmentView recordCommitment(@RequestBody RecordCommitmentBody body) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_WRITE);
        Objects.requireNonNull(body, "body");
        CommitmentConfirmation commitment = ledgerApi.recordCommitment(
                principal.tenantId(),
                body.meetingOccurrenceId(),
                body.noteId() == null ? UUID.randomUUID() : body.noteId(),
                body.text(),
                body.owner(),
                body.dueDate()
        );
        return CommitmentView.from(commitment);
    }

    @PostMapping("/commitments/{commitmentId}/confirm")
    @RequiresPermission(Permission.MEETING_WRITE)
    public CommitmentView confirmCommitment(@PathVariable UUID commitmentId) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_WRITE);
        try {
            return CommitmentView.from(ledgerApi.confirmCommitment(
                    principal.tenantId(),
                    commitmentId,
                    principal.userId()
            ));
        } catch (IllegalArgumentException ex) {
            throw new ActenoraException("INTELLIGENCE_RESOURCE_NOT_FOUND", ex.getMessage());
        }
    }

    @PostMapping("/commitments/{commitmentId}/reject")
    @RequiresPermission(Permission.MEETING_WRITE)
    public CommitmentView rejectCommitment(@PathVariable UUID commitmentId) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_WRITE);
        try {
            return CommitmentView.from(ledgerApi.rejectCommitment(
                    principal.tenantId(),
                    commitmentId,
                    principal.userId()
            ));
        } catch (IllegalArgumentException ex) {
            throw new ActenoraException("INTELLIGENCE_RESOURCE_NOT_FOUND", ex.getMessage());
        }
    }

    @GetMapping("/overdue-commitments")
    @RequiresPermission(Permission.MEETING_READ)
    public List<CommitmentView> overdueCommitments() {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_READ);
        return ledgerApi.overdueCommitments(principal.tenantId()).stream()
                .map(CommitmentView::from)
                .toList();
    }

    @GetMapping("/occurrences/{occurrenceId}/brief")
    @RequiresPermission(Permission.MEETING_READ)
    public MeetingBriefView brief(@PathVariable UUID occurrenceId) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_READ);
        return MeetingBriefView.from(ledgerApi.generateBrief(principal.tenantId(), occurrenceId));
    }

    @GetMapping("/occurrences/{occurrenceId}/continuity")
    @RequiresPermission(Permission.MEETING_READ)
    public ContinuityView continuity(@PathVariable UUID occurrenceId) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_READ);
        return ContinuityView.from(ledgerApi.continuity(principal.tenantId(), occurrenceId));
    }

    @PostMapping("/rebuild")
    @RequiresPermission(Permission.MEETING_WRITE)
    public RebuildProjectionsView rebuild() {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_WRITE);
        var state = ledgerApi.rebuildProjections(principal.tenantId());
        return RebuildProjectionsView.from(state, ledgerApi.listEvents(principal.tenantId()).size());
    }

    @ExceptionHandler(ActenoraException.class)
    public ResponseEntity<ProblemDetail> handleActenora(ActenoraException ex) {
        HttpStatus status = switch (ex.code()) {
            case "INTELLIGENCE_RESOURCE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "TENANT_ISOLATION_VIOLATION" -> HttpStatus.FORBIDDEN;
            case "INVALID_COMMITMENT_TRANSITION" -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(ex.code());
        problem.setProperty("code", ex.code());
        return ResponseEntity.status(status).body(problem);
    }

    public record RecordSuggestionBody(
            UUID sourceOccurrenceId,
            UUID targetOccurrenceId,
            String proposedRelation,
            BigDecimal confidence,
            String reason
    ) {
    }

    public record ProposeContradictionBody(
            UUID meetingOccurrenceId,
            UUID leftDecisionId,
            UUID rightDecisionId,
            String reason,
            BigDecimal confidence
    ) {
    }

    public record RecordCommitmentBody(
            UUID meetingOccurrenceId,
            UUID noteId,
            String text,
            String owner,
            LocalDate dueDate
    ) {
    }

    public record LedgerEventView(
            UUID eventId,
            LedgerEventType type,
            String aggregateType,
            UUID aggregateId,
            UUID meetingOccurrenceId,
            long sequence,
            Instant occurredAt,
            Map<String, String> payload
    ) {
        static LedgerEventView from(LedgerEvent event) {
            return new LedgerEventView(
                    event.eventId(),
                    event.type(),
                    event.aggregateType(),
                    event.aggregateId(),
                    event.meetingOccurrenceId(),
                    event.sequence(),
                    event.occurredAt(),
                    event.payload()
            );
        }
    }

    public record SuggestionView(
            UUID id,
            UUID sourceOccurrenceId,
            UUID targetOccurrenceId,
            String proposedRelation,
            BigDecimal confidence,
            String reason,
            ContinuitySuggestionStatus status
    ) {
        static SuggestionView from(ContinuityRelationSuggestion suggestion) {
            return new SuggestionView(
                    suggestion.id(),
                    suggestion.sourceOccurrenceId(),
                    suggestion.targetOccurrenceId(),
                    suggestion.proposedRelation().name(),
                    suggestion.confidence(),
                    suggestion.reason(),
                    suggestion.status()
            );
        }
    }

    public record ContradictionView(
            UUID id,
            UUID meetingOccurrenceId,
            UUID leftDecisionId,
            UUID rightDecisionId,
            String reason,
            BigDecimal confidence,
            ContradictionStatus status
    ) {
        static ContradictionView from(ContradictionCandidate candidate) {
            return new ContradictionView(
                    candidate.id(),
                    candidate.meetingOccurrenceId(),
                    candidate.leftDecisionId(),
                    candidate.rightDecisionId(),
                    candidate.reason(),
                    candidate.confidence(),
                    candidate.status()
            );
        }
    }

    public record CommitmentView(
            UUID commitmentId,
            UUID meetingOccurrenceId,
            String text,
            String owner,
            CommitmentConfirmationStatus status,
            LocalDate dueDate,
            boolean overdue
    ) {
        static CommitmentView from(CommitmentConfirmation commitment) {
            return new CommitmentView(
                    commitment.commitmentId(),
                    commitment.meetingOccurrenceId(),
                    commitment.text(),
                    commitment.owner().orElse(null),
                    commitment.status(),
                    commitment.dueDate().orElse(null),
                    commitment.overdue()
            );
        }
    }

    public record MeetingBriefView(
            UUID briefId,
            UUID targetOccurrenceId,
            UUID previousOccurrenceId,
            UUID meetingSeriesId,
            List<CarryOverView> openTasks,
            List<CarryOverView> openRisks,
            List<CarryOverView> unresolvedQuestions,
            List<UUID> followUpChain,
            List<CommitmentView> overdueCommitments,
            Instant generatedAt
    ) {
        static MeetingBriefView from(MeetingBrief brief) {
            return new MeetingBriefView(
                    brief.briefId(),
                    brief.targetOccurrenceId(),
                    brief.previousOccurrenceId().orElse(null),
                    brief.meetingSeriesId().orElse(null),
                    brief.openTasks().stream().map(CarryOverView::from).toList(),
                    brief.openRisks().stream().map(CarryOverView::from).toList(),
                    brief.unresolvedQuestions().stream().map(CarryOverView::from).toList(),
                    brief.followUpChain(),
                    brief.overdueCommitments().stream().map(CommitmentView::from).toList(),
                    brief.generatedAt()
            );
        }
    }

    public record CarryOverView(UUID itemId, String kind, String text, UUID sourceOccurrenceId) {
        static CarryOverView from(MeetingBrief.CarryOverItem item) {
            return new CarryOverView(item.itemId(), item.kind(), item.text(), item.sourceOccurrenceId());
        }
    }

    public record ContinuityView(
            UUID meetingOccurrenceId,
            UUID meetingSeriesId,
            UUID businessContextId,
            UUID previousOccurrenceId,
            UUID nextOccurrenceId,
            List<UUID> followUpChain
    ) {
        static ContinuityView from(ContinuityProjection projection) {
            return new ContinuityView(
                    projection.meetingOccurrenceId(),
                    projection.meetingSeriesId().orElse(null),
                    projection.businessContextId().orElse(null),
                    projection.previousOccurrenceId().orElse(null),
                    projection.nextOccurrenceId().orElse(null),
                    projection.followUpChain()
            );
        }
    }

    public record RebuildProjectionsView(
            int eventCount,
            int decisionCount,
            int commitmentCount,
            int suggestionCount,
            int contradictionCount,
            int continuityCount
    ) {
        static RebuildProjectionsView from(LedgerProjectionState state, int eventCount) {
            return new RebuildProjectionsView(
                    eventCount,
                    state.decisions().size(),
                    state.commitments().size(),
                    state.suggestions().size(),
                    state.contradictions().size(),
                    state.continuities().size()
            );
        }
    }
}
