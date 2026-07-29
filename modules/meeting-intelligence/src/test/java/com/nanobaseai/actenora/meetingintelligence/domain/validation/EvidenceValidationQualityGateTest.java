package com.nanobaseai.actenora.meetingintelligence.domain.validation;

import com.nanobaseai.actenora.meetingintelligence.application.validation.EvidenceValidationService;
import com.nanobaseai.actenora.meetingintelligence.api.OverrideQualityGateCommand;
import com.nanobaseai.actenora.meetingintelligence.api.RunValidationCommand;
import com.nanobaseai.actenora.meetingintelligence.api.ValidationExecutionResult;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.validation.InMemoryManualReviewCaseRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.validation.InMemoryQualityGatePolicyPort;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.validation.InMemoryValidationRunRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.validation.RecordingValidationAuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceValidationQualityGateTest {

    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");

    private UUID tenantId;
    private UUID occurrenceId;
    private UUID extractionId;
    private UUID segmentId;
    private UUID participantId;

    private InMemoryValidationRunRepository runRepository;
    private InMemoryManualReviewCaseRepository reviewRepository;
    private InMemoryQualityGatePolicyPort policyPort;
    private RecordingValidationAuditPort auditPort;
    private EvidenceValidationService service;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        occurrenceId = UUID.randomUUID();
        extractionId = UUID.randomUUID();
        segmentId = UUID.randomUUID();
        participantId = UUID.randomUUID();

        runRepository = new InMemoryValidationRunRepository();
        reviewRepository = new InMemoryManualReviewCaseRepository();
        policyPort = new InMemoryQualityGatePolicyPort();
        auditPort = new RecordingValidationAuditPort();
        service = new EvidenceValidationService(
                runRepository,
                reviewRepository,
                policyPort,
                auditPort,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void evidenceMissingRejects() {
        ValidationCandidate candidate = baseCandidate(segmentId)
                .evidenceSegmentIds(List.of())
                .build();

        ValidationExecutionResult result = validate(List.of(candidate), List.of(segment()), List.of(participant()));

        assertEquals(QualityGateOutcome.REJECTED, result.decision().outcome());
        assertTrue(hasFail(result, ValidationRuleCodes.EVIDENCE_SEGMENT_EXISTS));
        assertTrue(result.manualReviewCase().isPresent());
    }

    @Test
    void invalidOffsetRejects() {
        ValidationCandidate candidate = baseCandidate(segmentId)
                .claimedOffsets(0L, 50_000L)
                .build();

        ValidationExecutionResult result = validate(List.of(candidate), List.of(segment()), List.of(participant()));

        assertEquals(QualityGateOutcome.REJECTED, result.decision().outcome());
        assertTrue(hasFail(result, ValidationRuleCodes.OFFSET_VALID));
    }

    @Test
    void unknownOwnerRequiresManualReview() {
        ValidationCandidate candidate = baseCandidate(segmentId)
                .owner("unknown-user", "Ghost Owner")
                .build();

        ValidationExecutionResult result = validate(List.of(candidate), List.of(segment()), List.of(participant()));

        assertEquals(QualityGateOutcome.MANUAL_REVIEW_REQUIRED, result.decision().outcome());
        assertTrue(hasFail(result, ValidationRuleCodes.OWNER_IS_PARTICIPANT));
        assertTrue(result.manualReviewCase().isPresent());
    }

    @Test
    void unsupportedDueDateFails() {
        ValidationCandidate candidate = baseCandidate(segmentId)
                .dueDateText("2099-01-01")
                .build();

        ValidationExecutionResult result = validate(List.of(candidate), List.of(segment()), List.of(participant()));

        assertEquals(QualityGateOutcome.MANUAL_REVIEW_REQUIRED, result.decision().outcome());
        assertTrue(hasFail(result, ValidationRuleCodes.DUE_DATE_IN_TRANSCRIPT));
    }

    @Test
    void resolvedIsoDueDatePassesWhenRelativeDateInTranscript() {
        ValidationCandidate candidate = baseCandidate(segmentId)
                .dueDateText("2026-07-29")
                .relativeDateText("bugün 16.00'ya kadar")
                .build();

        ValidationSegment relativeSegment = new ValidationSegment(
                segmentId,
                0,
                "spk-1",
                "Ada Lovelace",
                0,
                10_000,
                "Ada will ship release notes bugün 16'ya kadar for 15000 TL. (ISO date not present)",
                true
        );

        ValidationExecutionResult result = validate(
                List.of(candidate),
                List.of(relativeSegment),
                List.of(participant())
        );

        assertEquals(QualityGateOutcome.PASSED, result.decision().outcome());
        assertTrue(!hasFail(result, ValidationRuleCodes.DUE_DATE_IN_TRANSCRIPT));
    }

    @Test
    void ownerFirstNameMatchesSpeakerFullName() {
        ValidationCandidate candidate = baseCandidate(segmentId)
                .owner("unknown-user", "Ada")
                .build();

        ValidationExecutionResult result = validate(List.of(candidate), List.of(segment()), List.of(participant()));

        assertEquals(QualityGateOutcome.PASSED, result.decision().outcome());
        assertTrue(!hasFail(result, ValidationRuleCodes.OWNER_IS_PARTICIPANT));
    }

    @Test
    void duplicateCandidateFails() {
        ValidationCandidate first = baseCandidate(segmentId).build();
        ValidationCandidate duplicate = ValidationCandidate.builder(
                        "cand-2",
                        CandidateKind.ACTION_ITEM,
                        "Ship release notes",
                        new BigDecimal("0.90")
                )
                .evidenceSegmentIds(List.of(segmentId))
                .build();

        ValidationExecutionResult result = validate(
                List.of(first, duplicate),
                List.of(segment()),
                List.of(participant())
        );

        assertTrue(hasFail(result, ValidationRuleCodes.DUPLICATE_CANDIDATE));
        assertEquals(QualityGateOutcome.MANUAL_REVIEW_REQUIRED, result.decision().outcome());
    }

    @Test
    void lowConfidenceRequiresManualReviewByDefault() {
        ValidationCandidate candidate = ValidationCandidate.builder(
                        "cand-1",
                        CandidateKind.ACTION_ITEM,
                        "Ship release notes",
                        new BigDecimal("0.20")
                )
                .evidenceSegmentIds(List.of(segmentId))
                .build();

        ValidationExecutionResult result = validate(List.of(candidate), List.of(segment()), List.of(participant()));

        assertEquals(QualityGateOutcome.MANUAL_REVIEW_REQUIRED, result.decision().outcome());
        assertTrue(hasFail(result, ValidationRuleCodes.CONFIDENCE_THRESHOLD));
    }

    @Test
    void manualOverrideIsAuditedAndPreservesHistory() {
        ValidationCandidate candidate = baseCandidate(segmentId)
                .owner("unknown-user", "Ghost Owner")
                .build();
        ValidationExecutionResult first = validate(List.of(candidate), List.of(segment()), List.of(participant()));
        assertEquals(QualityGateOutcome.MANUAL_REVIEW_REQUIRED, first.decision().outcome());

        QualityGateDecision overridden = service.override(new OverrideQualityGateCommand(
                tenantId,
                first.decision().id(),
                "reviewer@example.com",
                "Verified owner offline",
                QualityGateOutcome.PASSED_WITH_WARNINGS
        ));

        assertTrue(overridden.overridden());
        assertEquals(QualityGateOutcome.PASSED_WITH_WARNINGS, overridden.outcome());
        assertEquals(QualityGateOutcome.MANUAL_REVIEW_REQUIRED, overridden.originalOutcome().orElseThrow());
        assertTrue(auditPort.hasAction("QUALITY_GATE_OVERRIDDEN"));
        assertEquals(1, service.history(tenantId, extractionId).size());
    }

    @Test
    void tenantThresholdCanForceRejectOnLowConfidence() {
        policyPort.put(tenantId, new QualityGateThreshold(
                new BigDecimal("0.80"),
                true,
                2,
                Set.of(ValidationRuleCodes.EVIDENCE_SEGMENT_EXISTS, ValidationRuleCodes.OFFSET_VALID)
        ));

        ValidationCandidate candidate = ValidationCandidate.builder(
                        "cand-1",
                        CandidateKind.ACTION_ITEM,
                        "Ship release notes",
                        new BigDecimal("0.50")
                )
                .evidenceSegmentIds(List.of(segmentId))
                .build();

        ValidationExecutionResult result = validate(List.of(candidate), List.of(segment()), List.of(participant()));

        assertEquals(QualityGateOutcome.REJECTED, result.decision().outcome());
        assertTrue(hasFail(result, ValidationRuleCodes.CONFIDENCE_THRESHOLD));
    }

    @Test
    void revalidationAppendsWithoutDeletingPriorRuns() {
        ValidationCandidate ok = baseCandidate(segmentId).build();
        validate(List.of(ok), List.of(segment()), List.of(participant()));

        ValidationCandidate missing = baseCandidate(segmentId).evidenceSegmentIds(List.of()).build();
        validate(List.of(missing), List.of(segment()), List.of(participant()));

        assertEquals(2, service.history(tenantId, extractionId).size());
        assertEquals(2, runRepository.runCount());
        ValidationMetrics metrics = service.metricsForExtraction(tenantId, extractionId);
        assertEquals(2, metrics.totalRuns());
        assertEquals(1, metrics.count(QualityGateOutcome.PASSED));
        assertEquals(1, metrics.count(QualityGateOutcome.REJECTED));
    }

    @Test
    void amountMustAppearInTranscript() {
        ValidationCandidate candidate = baseCandidate(segmentId)
                .amountText("999999 TL")
                .build();

        ValidationExecutionResult result = validate(List.of(candidate), List.of(segment()), List.of(participant()));

        assertTrue(hasFail(result, ValidationRuleCodes.AMOUNT_IN_TRANSCRIPT));
    }

    @Test
    void suggestionMarkedAsDecisionFails() {
        ValidationCandidate candidate = ValidationCandidate.builder(
                        "cand-1",
                        CandidateKind.SUGGESTION,
                        "Maybe postpone launch",
                        new BigDecimal("0.90")
                )
                .evidenceSegmentIds(List.of(segmentId))
                .markedAsDecision(true)
                .build();

        ValidationExecutionResult result = validate(List.of(candidate), List.of(segment()), List.of(participant()));

        assertTrue(hasFail(result, ValidationRuleCodes.SUGGESTION_NOT_DECISION));
    }

    private ValidationExecutionResult validate(
            List<ValidationCandidate> candidates,
            List<ValidationSegment> segments,
            List<ValidationParticipant> participants
    ) {
        return service.validate(new RunValidationCommand(
                tenantId,
                occurrenceId,
                extractionId,
                candidates,
                segments,
                participants
        ));
    }

    private ValidationCandidate.Builder baseCandidate(UUID evidenceId) {
        return ValidationCandidate.builder(
                        "cand-1",
                        CandidateKind.ACTION_ITEM,
                        "Ship release notes",
                        new BigDecimal("0.90")
                )
                .evidenceSegmentIds(List.of(evidenceId));
    }

    private ValidationSegment segment() {
        return new ValidationSegment(
                segmentId,
                0,
                "spk-1",
                "Ada Lovelace",
                0,
                10_000,
                "Ada will ship release notes by 2026-08-01 for 15000 TL near the decision marker.",
                true
        );
    }

    private ValidationParticipant participant() {
        return new ValidationParticipant(participantId, "Ada Lovelace", "ada@example.com", "entra-ada");
    }

    private static boolean hasFail(ValidationExecutionResult result, String ruleId) {
        return result.run().ruleResults().stream()
                .anyMatch(r -> r.ruleId().equals(ruleId) && r.isFail());
    }
}
