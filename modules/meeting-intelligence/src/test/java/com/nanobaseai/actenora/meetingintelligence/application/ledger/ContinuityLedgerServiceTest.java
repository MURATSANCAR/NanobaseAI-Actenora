package com.nanobaseai.actenora.meetingintelligence.application.ledger;

import com.nanobaseai.actenora.meetingintelligence.domain.ledger.CommitmentConfirmation;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContinuityProjection;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContinuityRelationSuggestion;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContinuitySuggestionStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContradictionCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContradictionStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.DecisionHistory;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.DecisionHistoryEntry;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.MeetingBrief;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.projection.LedgerProjectionState;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ActionItemStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.CommitmentConfirmationStatus;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.ledger.InMemoryLedgerEventStore;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.ledger.InMemoryLedgerProjectionRepository;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContinuityLedgerServiceTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-25T12:00:00Z"), ZoneOffset.UTC);

    private ContinuityLedgerService service;
    private InMemoryLedgerEventStore eventStore;
    private InMemoryLedgerProjectionRepository projections;

    private UUID occPrevious;
    private UUID occNext;
    private UUID notePrevious;

    @BeforeEach
    void setUp() {
        eventStore = new InMemoryLedgerEventStore();
        projections = new InMemoryLedgerProjectionRepository();
        service = new ContinuityLedgerService(eventStore, projections, clock);

        occPrevious = UUID.randomUUID();
        occNext = UUID.randomUUID();
        notePrevious = UUID.randomUUID();
    }

    @Test
    void supersededDecisionKeepsHistoryChain() {
        DecisionHistoryEntry older = service.recordDecision(
                tenant, occPrevious, notePrevious, null, "Ship on Monday"
        );

        DecisionHistory history = service.supersedeDecision(
                tenant, occPrevious, notePrevious, older.decisionId(), "Ship on Wednesday"
        );

        assertEquals(2, history.chronological().size());
        assertFalse(history.chronological().getFirst().active());
        assertTrue(history.activeEntry().orElseThrow().active());
        assertEquals("Ship on Wednesday", history.activeEntry().orElseThrow().text());
        assertEquals(
                older.decisionId(),
                history.activeEntry().orElseThrow().supersedesDecisionId().orElseThrow()
        );
        assertEquals(
                history.activeEntry().orElseThrow().decisionId(),
                history.chronological().getFirst().supersededByDecisionId().orElseThrow()
        );
    }

    @Test
    void openTasksAreListedUntilCompleted() {
        UUID openId = service.recordActionItem(tenant, occPrevious, notePrevious, "Draft agenda");
        UUID doneId = service.recordActionItem(tenant, occPrevious, notePrevious, "Book room");
        service.transitionActionItem(tenant, doneId, ActionItemStatus.COMPLETED);

        var openTasks = service.listOpenTasks(tenant, occPrevious);

        assertEquals(1, openTasks.size());
        assertEquals(openId, openTasks.getFirst().id());
        assertEquals(ActionItemStatus.OPEN, openTasks.getFirst().status());
    }

    @Test
    void overdueCommitmentIsFlaggedWhenPastDue() {
        CommitmentConfirmation pending = service.recordCommitment(
                tenant,
                occPrevious,
                notePrevious,
                "Send report",
                "alice@example.com",
                LocalDate.parse("2026-07-20")
        );

        assertTrue(pending.overdue());
        assertEquals(CommitmentConfirmationStatus.PENDING_CONFIRMATION, pending.status());
        assertEquals(1, service.listOverdueCommitments(tenant).size());

        CommitmentConfirmation confirmed = service.confirmCommitment(
                tenant, pending.commitmentId(), UUID.randomUUID()
        );
        assertTrue(confirmed.overdue());
        assertEquals(CommitmentConfirmationStatus.CONFIRMED, confirmed.status());
    }

    @Test
    void briefGenerationCarriesOpenTasksRisksQuestionsAndFollowUp() {
        UUID seriesId = UUID.randomUUID();
        service.linkOccurrenceContinuity(tenant, occPrevious, seriesId, null, null);
        service.linkOccurrenceContinuity(tenant, occNext, seriesId, null, occPrevious);
        service.linkFollowUp(tenant, occPrevious, occNext);

        service.recordActionItem(tenant, occPrevious, notePrevious, "Open task A");
        service.recordRisk(tenant, occPrevious, notePrevious, "Vendor delay");
        service.recordOpenQuestion(tenant, occPrevious, notePrevious, "Budget approved?");
        service.recordDecision(tenant, occPrevious, notePrevious, null, "Keep hybrid format");
        service.recordCommitment(
                tenant, occPrevious, notePrevious, "Late deliverable", "bob", LocalDate.parse("2026-07-01")
        );

        MeetingBrief brief = service.generateBrief(tenant, occNext);

        assertEquals(occNext, brief.targetOccurrenceId());
        assertEquals(Optional.of(occPrevious), brief.previousOccurrenceId());
        assertEquals(1, brief.openTasks().size());
        assertEquals(1, brief.openRisks().size());
        assertEquals(1, brief.unresolvedQuestions().size());
        assertEquals(1, brief.activeDecisions().size());
        assertEquals(1, brief.overdueCommitments().size());
        assertTrue(brief.followUpChain().contains(occPrevious));
        assertTrue(brief.followUpChain().contains(occNext));
    }

    @Test
    void projectionRebuildRestoresStateFromEvents() {
        DecisionHistoryEntry decision = service.recordDecision(
                tenant, occPrevious, notePrevious, null, "Approve budget"
        );
        CommitmentConfirmation commitment = service.recordCommitment(
                tenant, occPrevious, notePrevious, "Pay invoice", null, LocalDate.parse("2026-07-10")
        );
        service.linkOccurrenceContinuity(tenant, occPrevious, UUID.randomUUID(), null, null);

        // Corrupt live projection then rebuild from event stream.
        projections.getOrCreate(tenant).clear();
        assertTrue(projections.getOrCreate(tenant).decisions().isEmpty());

        LedgerProjectionState rebuilt = service.rebuildProjections(tenant);

        assertEquals(1, rebuilt.decisions().size());
        assertEquals(decision.decisionId(), rebuilt.decision(decision.decisionId()).orElseThrow().decisionId());
        assertTrue(rebuilt.commitment(commitment.commitmentId()).orElseThrow().overdue());
        assertTrue(rebuilt.continuity(occPrevious).isPresent());
        assertEquals(eventStore.findAllByTenant(tenant).size(), 3);
    }

    @Test
    void relationSuggestionApproveMaterializesFollowUp_rejectDoesNot() {
        ContinuityRelationSuggestion suggestion = service.recordRelationSuggestion(
                tenant,
                occPrevious,
                occNext,
                ContinuityRelationSuggestion.ProposedRelation.FOLLOW_UP,
                new BigDecimal("0.88"),
                "Agenda continuity"
        );
        assertEquals(ContinuitySuggestionStatus.PENDING, suggestion.status());
        assertTrue(service.continuity(tenant, occPrevious).followUpChain().isEmpty());

        service.decideRelationSuggestion(tenant, suggestion.id(), true, "approver-1");
        ContinuityProjection afterApprove = service.continuity(tenant, occPrevious);
        assertTrue(afterApprove.followUpChain().contains(occNext));
        assertEquals(Optional.of(occNext), afterApprove.nextOccurrenceId());

        ContinuityRelationSuggestion rejected = service.recordRelationSuggestion(
                tenant,
                occPrevious,
                UUID.randomUUID(),
                ContinuityRelationSuggestion.ProposedRelation.SAME_SERIES,
                new BigDecimal("0.40"),
                "Weak title match"
        );
        Optional<ContinuityRelationSuggestion> result =
                service.decideRelationSuggestion(tenant, rejected.id(), false, "approver-1");
        assertTrue(result.isEmpty());
        assertEquals(
                ContinuitySuggestionStatus.REJECTED,
                projections.getOrCreate(tenant).suggestion(rejected.id()).orElseThrow().status()
        );
    }

    @Test
    void contradictionRequiresHumanConfirmation() {
        DecisionHistoryEntry left = service.recordDecision(
                tenant, occPrevious, notePrevious, null, "Go remote"
        );
        DecisionHistoryEntry right = service.recordDecision(
                tenant, occPrevious, notePrevious, null, "Stay in office"
        );

        ContradictionCandidate pending = service.proposeContradiction(
                tenant,
                occPrevious,
                left.decisionId(),
                right.decisionId(),
                "Mutually exclusive location decisions",
                new BigDecimal("0.93")
        );
        assertEquals(ContradictionStatus.PENDING, pending.status());

        ContradictionCandidate confirmed = service.decideContradiction(
                tenant, pending.id(), true, "reviewer-1"
        );
        assertEquals(ContradictionStatus.CONFIRMED, confirmed.status());
        assertEquals(Optional.of("reviewer-1"), confirmed.decidedBy());

        ContradictionCandidate other = service.proposeContradiction(
                tenant,
                occPrevious,
                left.decisionId(),
                right.decisionId(),
                "Duplicate candidate",
                new BigDecimal("0.70")
        );
        ContradictionCandidate rejected = service.decideContradiction(
                tenant, other.id(), false, "reviewer-1"
        );
        assertEquals(ContradictionStatus.REJECTED, rejected.status());
    }

    @Test
    void sameSeriesAndSameBusinessContextProjection() {
        UUID seriesId = UUID.randomUUID();
        UUID contextId = UUID.randomUUID();
        UUID occMid = UUID.randomUUID();

        service.linkOccurrenceContinuity(tenant, occPrevious, seriesId, contextId, null);
        service.linkOccurrenceContinuity(tenant, occMid, seriesId, contextId, occPrevious);
        service.linkOccurrenceContinuity(tenant, occNext, seriesId, contextId, occMid);

        ContinuityProjection mid = service.continuity(tenant, occMid);
        assertEquals(Optional.of(seriesId), mid.meetingSeriesId());
        assertEquals(Optional.of(contextId), mid.businessContextId());
        assertTrue(mid.sameSeriesOccurrenceIds().contains(occPrevious));
        assertTrue(mid.sameSeriesOccurrenceIds().contains(occNext));
        assertTrue(mid.sameBusinessContextOccurrenceIds().contains(occPrevious));
        assertEquals(Optional.of(occPrevious), mid.previousOccurrenceId());
        assertEquals(Optional.of(occNext), mid.nextOccurrenceId());
    }
}
