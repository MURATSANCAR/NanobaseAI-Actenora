package com.nanobaseai.actenora.meetingintelligence.application.validation;

import com.nanobaseai.actenora.meetingintelligence.api.OverrideQualityGateCommand;
import com.nanobaseai.actenora.meetingintelligence.api.RunValidationCommand;
import com.nanobaseai.actenora.meetingintelligence.api.ValidationExecutionResult;
import com.nanobaseai.actenora.meetingintelligence.application.validation.port.ManualReviewCaseRepository;
import com.nanobaseai.actenora.meetingintelligence.application.validation.port.QualityGatePolicyPort;
import com.nanobaseai.actenora.meetingintelligence.application.validation.port.ValidationAuditPort;
import com.nanobaseai.actenora.meetingintelligence.application.validation.port.ValidationRunRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ManualReviewCase;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.QualityGateDecision;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.QualityGateEvaluator;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.QualityGateOutcome;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.QualityGateThreshold;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationContext;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationMetrics;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRuleEngine;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRuleResult;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRun;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application façade for evidence validation and the AI quality gate.
 * Re-validation always appends a new {@link ValidationRun}; prior results are retained.
 */
public final class EvidenceValidationService {

    private final ValidationRunRepository runRepository;
    private final ManualReviewCaseRepository reviewRepository;
    private final QualityGatePolicyPort policyPort;
    private final ValidationAuditPort auditPort;
    private final ValidationRuleEngine ruleEngine;
    private final QualityGateEvaluator gateEvaluator;
    private final Clock clock;

    public EvidenceValidationService(
            ValidationRunRepository runRepository,
            ManualReviewCaseRepository reviewRepository,
            QualityGatePolicyPort policyPort,
            ValidationAuditPort auditPort,
            Clock clock
    ) {
        this(
                runRepository,
                reviewRepository,
                policyPort,
                auditPort,
                ValidationRuleEngine.withDefaults(),
                new QualityGateEvaluator(),
                clock
        );
    }

    public EvidenceValidationService(
            ValidationRunRepository runRepository,
            ManualReviewCaseRepository reviewRepository,
            QualityGatePolicyPort policyPort,
            ValidationAuditPort auditPort,
            ValidationRuleEngine ruleEngine,
            QualityGateEvaluator gateEvaluator,
            Clock clock
    ) {
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository");
        this.reviewRepository = Objects.requireNonNull(reviewRepository, "reviewRepository");
        this.policyPort = Objects.requireNonNull(policyPort, "policyPort");
        this.auditPort = Objects.requireNonNull(auditPort, "auditPort");
        this.ruleEngine = Objects.requireNonNull(ruleEngine, "ruleEngine");
        this.gateEvaluator = Objects.requireNonNull(gateEvaluator, "gateEvaluator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ValidationExecutionResult validate(RunValidationCommand command) {
        Instant now = clock.instant();
        QualityGateThreshold threshold = policyPort.thresholdFor(command.tenantId());
        ValidationContext context = new ValidationContext(
                command.tenantId(),
                command.meetingOccurrenceId(),
                command.sourceExtractionId(),
                command.candidates(),
                command.segments(),
                command.participants(),
                threshold
        );

        List<ValidationRuleResult> ruleResults = ruleEngine.evaluate(context);
        QualityGateOutcome outcome = gateEvaluator.evaluate(ruleResults, threshold);
        ValidationRun run = ValidationRun.create(
                command.tenantId(),
                command.meetingOccurrenceId(),
                command.sourceExtractionId(),
                ruleResults,
                outcome,
                ValidationRuleEngine.ENGINE_VERSION,
                now
        );
        ValidationRun savedRun = runRepository.saveRun(run);
        QualityGateDecision decision = runRepository.saveDecision(QualityGateDecision.fromRun(savedRun, now));

        Optional<ManualReviewCase> reviewCase = Optional.empty();
        if (outcome == QualityGateOutcome.MANUAL_REVIEW_REQUIRED || outcome == QualityGateOutcome.REJECTED) {
            ManualReviewCase opened = ManualReviewCase.open(
                    command.tenantId(),
                    savedRun.id(),
                    decision.id(),
                    command.meetingOccurrenceId(),
                    reviewReason(outcome, ruleResults),
                    now
            );
            reviewCase = Optional.of(reviewRepository.save(opened));
        }

        auditPort.record(
                command.tenantId(),
                "system",
                "VALIDATION_RUN_COMPLETED",
                "ValidationRun",
                savedRun.id(),
                Map.of(
                        "outcome", outcome.name(),
                        "failCount", savedRun.failCount(),
                        "warnCount", savedRun.warnCount(),
                        "engineVersion", savedRun.engineVersion(),
                        "priorRunCount", Math.max(0, runRepository.findRunsByExtraction(
                                command.tenantId(), command.sourceExtractionId()).size() - 1)
                ),
                now
        );

        return new ValidationExecutionResult(
                savedRun,
                decision,
                reviewCase,
                ValidationMetrics.fromSingleRun(savedRun)
        );
    }

    public QualityGateDecision override(OverrideQualityGateCommand command) {
        Instant now = clock.instant();
        QualityGateDecision existing = runRepository.findDecision(command.tenantId(), command.decisionId())
                .orElseThrow(() -> new IllegalArgumentException("quality gate decision not found"));

        QualityGateDecision overridden = existing.override(
                command.actor(),
                command.reason(),
                command.newOutcome(),
                now
        );
        QualityGateDecision saved = runRepository.saveDecision(overridden);

        reviewRepository.findOpenByDecision(command.tenantId(), saved.id()).ifPresent(openCase -> {
            ManualReviewCase resolved = command.newOutcome() == QualityGateOutcome.REJECTED
                    ? openCase.resolveRejected(command.actor(), command.reason(), now)
                    : openCase.resolveOverride(command.actor(), command.reason(), now);
            reviewRepository.save(resolved);
        });

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("from", existing.outcome().name());
        metadata.put("to", saved.outcome().name());
        metadata.put("reason", command.reason());
        metadata.put("validationRunId", saved.validationRunId().toString());
        auditPort.record(
                command.tenantId(),
                command.actor(),
                "QUALITY_GATE_OVERRIDDEN",
                "QualityGateDecision",
                saved.id(),
                metadata,
                now
        );
        return saved;
    }

    public ValidationMetrics metricsForTenant(UUID tenantId) {
        return ValidationMetrics.fromRuns(runRepository.findRunsByTenant(tenantId));
    }

    public ValidationMetrics metricsForExtraction(UUID tenantId, UUID sourceExtractionId) {
        return ValidationMetrics.fromRuns(runRepository.findRunsByExtraction(tenantId, sourceExtractionId));
    }

    public List<ValidationRun> history(UUID tenantId, UUID sourceExtractionId) {
        return runRepository.findRunsByExtraction(tenantId, sourceExtractionId);
    }

    private static String reviewReason(QualityGateOutcome outcome, List<ValidationRuleResult> results) {
        String failedRules = results.stream()
                .filter(ValidationRuleResult::isFail)
                .map(ValidationRuleResult::ruleId)
                .distinct()
                .collect(Collectors.joining(", "));
        return outcome.name() + (failedRules.isBlank() ? "" : ": " + failedRules);
    }
}
