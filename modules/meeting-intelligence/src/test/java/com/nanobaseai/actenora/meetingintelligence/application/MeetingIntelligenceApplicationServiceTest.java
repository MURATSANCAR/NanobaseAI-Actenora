package com.nanobaseai.actenora.meetingintelligence.application;

import com.nanobaseai.actenora.meetingintelligence.api.dto.ActionItemCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.api.dto.ActionItemUpdateRequest;
import com.nanobaseai.actenora.meetingintelligence.api.dto.AiCandidateBundle;
import com.nanobaseai.actenora.meetingintelligence.api.dto.CommitmentCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.api.dto.CommitmentDecisionRequest;
import com.nanobaseai.actenora.meetingintelligence.api.dto.DecisionCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.api.dto.DecisionUpdateRequest;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MapAiCandidatesCommand;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteDetailResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteUpdateRequest;
import com.nanobaseai.actenora.meetingintelligence.api.dto.MeetingNoteVersionResponse;
import com.nanobaseai.actenora.meetingintelligence.api.dto.OpenQuestionCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.api.dto.RiskCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.api.dto.RiskUpdateRequest;
import com.nanobaseai.actenora.meetingintelligence.api.dto.VersionCompareResponse;
import com.nanobaseai.actenora.meetingintelligence.application.mapping.MapAiCandidatesToNoteService;
import com.nanobaseai.actenora.meetingintelligence.domain.exception.AiConfidenceIsNotApprovalException;
import com.nanobaseai.actenora.meetingintelligence.domain.exception.InvalidActionItemTransitionException;
import com.nanobaseai.actenora.meetingintelligence.domain.exception.InvalidCommitmentTransitionException;
import com.nanobaseai.actenora.meetingintelligence.domain.exception.NoteVersionImmutableException;
import com.nanobaseai.actenora.meetingintelligence.domain.exception.OptimisticLockConflictException;
import com.nanobaseai.actenora.meetingintelligence.domain.exception.TenantIsolationViolationException;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ActionItemStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.CommitmentConfirmationStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.Decision;
import com.nanobaseai.actenora.meetingintelligence.domain.model.HumanApprovalStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNoteVersion;
import com.nanobaseai.actenora.meetingintelligence.domain.model.NoteReviewStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.NoteVersionSource;
import com.nanobaseai.actenora.meetingintelligence.domain.model.QualityFlagCode;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.FixedClockPort;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryActionItemRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryCommitmentRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryDecisionRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryEvidenceLinkRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryMeetingNoteRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryMeetingNoteVersionRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryImportantFactRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryTopicRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryIssueRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryOpenQuestionRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryProposalRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryQualityFlagRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.InMemoryRiskRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.StaticTenantContextPort;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeetingIntelligenceApplicationServiceTest {

    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final UUID meetingOccurrenceId = UUID.randomUUID();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-25T12:00:00Z"), ZoneOffset.UTC);

    private InMemoryMeetingNoteRepository notes;
    private InMemoryMeetingNoteVersionRepository versions;
    private InMemoryDecisionRepository decisions;
    private InMemoryActionItemRepository actionItems;
    private InMemoryRiskRepository risks;
    private InMemoryCommitmentRepository commitments;
    private InMemoryOpenQuestionRepository openQuestions;
    private InMemoryIssueRepository issues;
    private InMemoryProposalRepository proposals;
    private InMemoryImportantFactRepository importantFacts;
    private InMemoryTopicRepository topics;
    private InMemoryEvidenceLinkRepository evidenceLinks;
    private InMemoryQualityFlagRepository qualityFlags;
    private StaticTenantContextPort tenantContext;
    private MeetingIntelligenceApplicationService service;

    @BeforeEach
    void setUp() {
        notes = new InMemoryMeetingNoteRepository();
        versions = new InMemoryMeetingNoteVersionRepository();
        decisions = new InMemoryDecisionRepository();
        actionItems = new InMemoryActionItemRepository();
        risks = new InMemoryRiskRepository();
        commitments = new InMemoryCommitmentRepository();
        openQuestions = new InMemoryOpenQuestionRepository();
        issues = new InMemoryIssueRepository();
        proposals = new InMemoryProposalRepository();
        importantFacts = new InMemoryImportantFactRepository();
        topics = new InMemoryTopicRepository();
        evidenceLinks = new InMemoryEvidenceLinkRepository();
        qualityFlags = new InMemoryQualityFlagRepository();
        tenantContext = new StaticTenantContextPort(TenantId.of(tenantA), actor);

        FixedClockPort clockPort = new FixedClockPort(clock);
        MapAiCandidatesToNoteService mapping = new MapAiCandidatesToNoteService(
                notes, versions, decisions, actionItems, risks, commitments,
                openQuestions, issues, proposals, importantFacts, topics, evidenceLinks, qualityFlags, clockPort
        );
        service = new MeetingIntelligenceApplicationService(
                tenantContext, clockPort, mapping, notes, versions, decisions, actionItems,
                risks, commitments, openQuestions, issues, proposals, importantFacts, topics, evidenceLinks, qualityFlags
        );
    }

    @Test
    void versionImmutability_humanEditCreatesNewVersion() {
        MeetingNoteDetailResponse created = mapFullyEvidencedNote();
        MeetingNoteVersion v1 = versions.findByIdAndTenantId(
                created.currentVersion().id(), TenantId.of(tenantA)).orElseThrow();
        assertThrows(NoteVersionImmutableException.class, v1::assertImmutable);

        MeetingNoteDetailResponse updated = service.updateNote(created.id(), new MeetingNoteUpdateRequest(
                "Corrected summary",
                "Fixed inaccurate AI phrasing",
                created.version()
        ));

        assertEquals(2, updated.currentVersionNumber());
        assertEquals(NoteVersionSource.HUMAN_EDIT, updated.currentVersion().source());
        assertEquals("Fixed inaccurate AI phrasing", updated.currentVersion().correctionReason());
        assertEquals(2, service.listVersions(created.id()).size());
        assertNotEquals(v1.id(), updated.currentVersion().id());
        assertEquals(v1.executiveSummary(), versions.findByIdAndTenantId(v1.id(), TenantId.of(tenantA))
                .orElseThrow().executiveSummary());
    }

    @Test
    void evidenceRequirement_marksManualReviewWhenMissing() {
        MeetingNoteDetailResponse note = service.mapAiCandidates(new MapAiCandidatesCommand(
                tenantA,
                meetingOccurrenceId,
                new AiCandidateBundle(
                        "Summary",
                        List.of(new DecisionCandidateInput("Ship it", List.of(), 0.9)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0.8),
                "model-qwen",
                "prompt-v3",
                "schema-v1",
                0.8
        ));

        assertEquals(NoteReviewStatus.MANUAL_REVIEW, note.reviewStatus());
        assertTrue(note.decisions().getFirst().requiresManualReview());
        assertTrue(note.qualityFlags().stream().anyMatch(f -> f.code() == QualityFlagCode.MISSING_EVIDENCE));
        assertTrue(note.evidenceLinks().isEmpty()
                || note.evidenceLinks().stream().noneMatch(e -> e.subjectId().equals(note.decisions().getFirst().id())));
    }

    @Test
    void decisionSupersede_linksDirectedRelationship() {
        MeetingNoteDetailResponse note = mapFullyEvidencedNote();
        UUID olderId = note.decisions().getFirst().id();

        // add a second decision via direct repo for supersede target
        Decision newer = Decision.createFromMapping(
                TenantId.of(tenantA),
                note.id(),
                note.currentVersion().id(),
                "Revised decision",
                false,
                0.95,
                clock.instant()
        );
        decisions.save(newer);

        var response = service.updateDecision(newer.id(), new DecisionUpdateRequest(
                null, olderId, 0L
        ));

        assertEquals(olderId, response.supersedesDecisionId());
        Decision older = decisions.findByIdAndTenantId(olderId, TenantId.of(tenantA)).orElseThrow();
        assertEquals(newer.id(), older.supersededByDecisionId());
    }

    @Test
    void actionTransitions_followStateMachine() {
        MeetingNoteDetailResponse note = mapFullyEvidencedNote();
        UUID actionId = note.actionItems().getFirst().id();

        var inProgress = service.updateActionItem(actionId, new ActionItemUpdateRequest(
                null, null, null, ActionItemStatus.IN_PROGRESS, 0L
        ));
        assertEquals(ActionItemStatus.IN_PROGRESS, inProgress.status());

        var completed = service.updateActionItem(actionId, new ActionItemUpdateRequest(
                null, null, null, ActionItemStatus.COMPLETED, inProgress.version()
        ));
        assertEquals(ActionItemStatus.COMPLETED, completed.status());

        assertThrows(InvalidActionItemTransitionException.class, () ->
                service.updateActionItem(actionId, new ActionItemUpdateRequest(
                        null, null, null, ActionItemStatus.OPEN, completed.version()
                )));
    }

    @Test
    void commitmentTransitions_approveAndReject() {
        MeetingNoteDetailResponse note = mapFullyEvidencedNote();
        UUID commitmentId = note.commitments().getFirst().id();

        var approved = service.approveCommitment(commitmentId, new CommitmentDecisionRequest(0L));
        assertEquals(CommitmentConfirmationStatus.CONFIRMED, approved.confirmationStatus());
        assertEquals(actor, approved.decidedByUserId());

        assertThrows(InvalidCommitmentTransitionException.class, () ->
                service.rejectCommitment(commitmentId, new CommitmentDecisionRequest(approved.version())));
    }

    @Test
    void optimisticLocking_rejectsStaleVersion() {
        MeetingNoteDetailResponse note = mapFullyEvidencedNote();
        assertThrows(OptimisticLockConflictException.class, () ->
                service.updateNote(note.id(), new MeetingNoteUpdateRequest(
                        "x", "reason", note.version() + 5
                )));
    }

    @Test
    void tenantIsolation_blocksCrossTenantAccess() {
        MeetingNoteDetailResponse note = mapFullyEvidencedNote();
        tenantContext = new StaticTenantContextPort(TenantId.of(tenantB), actor);
        FixedClockPort clockPort = new FixedClockPort(clock);
        service = new MeetingIntelligenceApplicationService(
                tenantContext, clockPort,
                new MapAiCandidatesToNoteService(
                        notes, versions, decisions, actionItems, risks, commitments,
                        openQuestions, issues, proposals, importantFacts, evidenceLinks, qualityFlags, clockPort
                ),
                notes, versions, decisions, actionItems, risks, commitments,
                openQuestions, issues, proposals, importantFacts, evidenceLinks, qualityFlags
        );

        assertThrows(com.nanobaseai.actenora.meetingintelligence.domain.exception.MeetingNoteNotFoundException.class,
                () -> service.noteDetail(note.id()));

        Decision decision = decisions.findByIdAndTenantId(note.decisions().getFirst().id(), TenantId.of(tenantA))
                .orElseThrow();
        assertThrows(TenantIsolationViolationException.class, () -> decision.assertTenant(TenantId.of(tenantB)));
    }

    @Test
    void provenance_isStoredSeparatelyFromHumanApproval() {
        MeetingNoteDetailResponse note = mapFullyEvidencedNote();
        MeetingNoteVersionResponse version = note.currentVersion();
        assertNotNull(version.provenance());
        assertEquals("model-qwen", version.provenance().modelId());
        assertEquals("prompt-v3", version.provenance().promptVersionId());
        assertEquals("schema-v1", version.provenance().schemaId());
        assertEquals(0.88, version.provenance().aiConfidence());
        assertEquals(HumanApprovalStatus.NONE, note.decisions().getFirst().humanApprovalStatus());
        assertEquals(0.91, note.decisions().getFirst().aiConfidence());

        Decision decision = decisions.findByIdAndTenantId(note.decisions().getFirst().id(), TenantId.of(tenantA))
                .orElseThrow();
        assertThrows(AiConfidenceIsNotApprovalException.class, () -> decision.rejectAiConfidenceAsApproval(0.99));
    }

    @Test
    void versionCompare_detectsSummaryChange() {
        MeetingNoteDetailResponse created = mapFullyEvidencedNote();
        service.updateNote(created.id(), new MeetingNoteUpdateRequest(
                "Changed summary", "typo fix", created.version()
        ));
        VersionCompareResponse compare = service.compareVersions(created.id(), 1, 2);
        assertTrue(compare.summaryChanged());
        assertEquals(1, compare.from().versionNumber());
        assertEquals(2, compare.to().versionNumber());
    }

    @Test
    void riskUpdate_supportsOptimisticLock() {
        MeetingNoteDetailResponse note = mapFullyEvidencedNote();
        UUID riskId = note.risks().getFirst().id();
        var updated = service.updateRisk(riskId, new RiskUpdateRequest("Updated risk", 0L));
        assertEquals("Updated risk", updated.text());
        assertEquals(1L, updated.version());
    }

    @Test
    void mappedChildrenSurviveSharedStoreReload() {
        MeetingNoteDetailResponse created = mapFullyEvidencedNote();
        UUID noteId = created.id();

        FixedClockPort clockPort = new FixedClockPort(clock);
        MapAiCandidatesToNoteService mapping = new MapAiCandidatesToNoteService(
                notes, versions, decisions, actionItems, risks, commitments,
                openQuestions, issues, proposals, importantFacts, evidenceLinks, qualityFlags, clockPort
        );
        MeetingIntelligenceApplicationService reloaded = new MeetingIntelligenceApplicationService(
                tenantContext, clockPort, mapping, notes, versions, decisions, actionItems,
                risks, commitments, openQuestions, issues, proposals, importantFacts, evidenceLinks, qualityFlags
        );

        MeetingNoteDetailResponse detail = reloaded.noteDetail(noteId);
        assertEquals(1, detail.decisions().size());
        assertEquals(1, detail.actionItems().size());
        assertEquals(1, detail.risks().size());
        assertEquals(1, detail.commitments().size());
        assertEquals(1, detail.openQuestions().size());
        assertEquals("Approve budget", detail.decisions().getFirst().text());
    }

    private MeetingNoteDetailResponse mapFullyEvidencedNote() {
        return service.mapAiCandidates(new MapAiCandidatesCommand(
                tenantA,
                meetingOccurrenceId,
                new AiCandidateBundle(
                        "Executive summary",
                        List.of(new DecisionCandidateInput("Approve budget", List.of("seg-1"), 0.91)),
                        List.of(new ActionItemCandidateInput("Draft plan", "alice", "2026-08-01", List.of("seg-2"), 0.8)),
                        List.of(new RiskCandidateInput("Vendor delay", List.of("seg-3"), 0.7)),
                        List.of(new OpenQuestionCandidateInput("Who owns QA?", List.of("seg-4"), 0.6)),
                        List.of(new CommitmentCandidateInput("Deliver by Friday", "bob", List.of("seg-5"), 0.85)), List.of(), List.of(), List.of(), List.of("LOW_CONFIDENCE"), List.of("seg-root"), 0.88),
                "model-qwen",
                "prompt-v3",
                "schema-v1",
                0.88
        ));
    }
}
