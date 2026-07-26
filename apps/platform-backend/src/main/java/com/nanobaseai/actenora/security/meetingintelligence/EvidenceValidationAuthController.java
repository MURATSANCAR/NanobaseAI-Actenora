package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.api.RequiresPermission;
import com.nanobaseai.actenora.identity.domain.Permission;
import com.nanobaseai.actenora.meetingintelligence.api.EvidenceValidationApi;
import com.nanobaseai.actenora.meetingintelligence.api.OverrideQualityGateCommand;
import com.nanobaseai.actenora.meetingintelligence.api.RunValidationCommand;
import com.nanobaseai.actenora.meetingintelligence.api.ValidationExecutionResult;
import com.nanobaseai.actenora.meetingintelligence.application.validation.port.ManualReviewCaseRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ManualReviewCase;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ManualReviewStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.QualityGateDecision;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.QualityGateOutcome;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationMetrics;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationParticipant;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationRun;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationSegment;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Auth-bound evidence validation / quality-gate HTTP surface (FAZ 17).
 */
@RestController
@RequestMapping("/api/v1")
public class EvidenceValidationAuthController {

    private final EvidenceValidationApi validationApi;
    private final ManualReviewCaseRepository reviewCases;
    private final IdentityApi identityApi;

    public EvidenceValidationAuthController(
            EvidenceValidationApi validationApi,
            ManualReviewCaseRepository reviewCases,
            IdentityApi identityApi
    ) {
        this.validationApi = Objects.requireNonNull(validationApi, "validationApi");
        this.reviewCases = Objects.requireNonNull(reviewCases, "reviewCases");
        this.identityApi = Objects.requireNonNull(identityApi, "identityApi");
    }

    @PostMapping("/evidence-validation/validate")
    @RequiresPermission(Permission.MEETING_WRITE)
    public ValidationResultView validate(@RequestBody ValidateRequest body) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_WRITE);
        UUID tenantId = principal.tenantId().value();
        ValidationExecutionResult result = validationApi.validate(new RunValidationCommand(
                tenantId,
                body.meetingOccurrenceId(),
                body.sourceExtractionId(),
                body.candidates() == null ? List.of() : body.candidates(),
                body.segments() == null ? List.of() : body.segments(),
                body.participants() == null ? List.of() : body.participants()
        ));
        return ValidationResultView.from(result);
    }

    @PostMapping("/evidence-validation/decisions/{decisionId}/override")
    @RequiresPermission(Permission.MEETING_WRITE)
    public QualityGateDecisionView override(
            @PathVariable UUID decisionId,
            @RequestBody OverrideRequest body
    ) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_WRITE);
        QualityGateDecision decision = validationApi.override(new OverrideQualityGateCommand(
                principal.tenantId().value(),
                decisionId,
                principal.email() == null ? principal.userId().toString() : principal.email(),
                body.reason(),
                body.newOutcome()
        ));
        return QualityGateDecisionView.from(decision);
    }

    @GetMapping("/evidence-validation/extractions/{sourceExtractionId}/history")
    @RequiresPermission(Permission.MEETING_READ)
    public List<ValidationRunView> history(@PathVariable UUID sourceExtractionId) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_READ);
        return validationApi.history(principal.tenantId().value(), sourceExtractionId).stream()
                .map(ValidationRunView::from)
                .toList();
    }

    @GetMapping("/evidence-validation/extractions/{sourceExtractionId}/metrics")
    @RequiresPermission(Permission.MEETING_READ)
    public ValidationMetricsView metrics(@PathVariable UUID sourceExtractionId) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_READ);
        return ValidationMetricsView.from(
                validationApi.metricsForExtraction(principal.tenantId().value(), sourceExtractionId));
    }

    @GetMapping("/evidence-validation/manual-review-cases")
    @RequiresPermission(Permission.MEETING_READ)
    public List<ManualReviewCaseView> manualReviewCases(
            @RequestParam(value = "status", defaultValue = "OPEN") ManualReviewStatus status
    ) {
        AuthenticatedPrincipal principal = TenantSecurityContext.require();
        identityApi.requirePermission(principal, Permission.MEETING_READ);
        return reviewCases.findByTenant(principal.tenantId().value(), status).stream()
                .map(ManualReviewCaseView::from)
                .toList();
    }

    @ExceptionHandler(ActenoraException.class)
    public ResponseEntity<ProblemDetail> handleActenora(ActenoraException ex) {
        HttpStatus status = switch (ex.code()) {
            case "TENANT_ISOLATION_VIOLATION" -> HttpStatus.FORBIDDEN;
            case "INTELLIGENCE_RESOURCE_NOT_FOUND", "MEETING_NOTE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(ex.code());
        problem.setProperty("code", ex.code());
        return ResponseEntity.status(status).body(problem);
    }

    public record ValidateRequest(
            UUID meetingOccurrenceId,
            UUID sourceExtractionId,
            List<ValidationCandidate> candidates,
            List<ValidationSegment> segments,
            List<ValidationParticipant> participants
    ) {
    }

    public record OverrideRequest(String reason, QualityGateOutcome newOutcome) {
    }

    public record ValidationResultView(
            UUID runId,
            UUID decisionId,
            QualityGateOutcome outcome,
            UUID manualReviewCaseId,
            long failCount,
            long warnCount,
            long passCount
    ) {
        static ValidationResultView from(ValidationExecutionResult result) {
            return new ValidationResultView(
                    result.run().id(),
                    result.decision().id(),
                    result.decision().outcome(),
                    result.manualReviewCase().map(ManualReviewCase::id).orElse(null),
                    result.run().failCount(),
                    result.run().warnCount(),
                    Math.max(0, result.run().ruleResults().size() - result.run().failCount() - result.run().warnCount())
            );
        }
    }

    public record QualityGateDecisionView(
            UUID id,
            UUID validationRunId,
            QualityGateOutcome outcome,
            boolean overridden,
            QualityGateOutcome originalOutcome
    ) {
        static QualityGateDecisionView from(QualityGateDecision decision) {
            return new QualityGateDecisionView(
                    decision.id(),
                    decision.validationRunId(),
                    decision.outcome(),
                    decision.overridden(),
                    decision.originalOutcome().orElse(null)
            );
        }
    }

    public record ValidationRunView(
            UUID id,
            UUID sourceExtractionId,
            QualityGateOutcome outcome,
            String engineVersion
    ) {
        static ValidationRunView from(ValidationRun run) {
            return new ValidationRunView(
                    run.id(),
                    run.sourceExtractionId(),
                    run.computedOutcome(),
                    run.engineVersion()
            );
        }
    }

    public record ValidationMetricsView(long pass, long passWithWarnings, long manualReview, long rejected) {
        static ValidationMetricsView from(ValidationMetrics metrics) {
            return new ValidationMetricsView(
                    metrics.count(QualityGateOutcome.PASSED),
                    metrics.count(QualityGateOutcome.PASSED_WITH_WARNINGS),
                    metrics.count(QualityGateOutcome.MANUAL_REVIEW_REQUIRED),
                    metrics.count(QualityGateOutcome.REJECTED)
            );
        }
    }

    public record ManualReviewCaseView(
            UUID id,
            UUID decisionId,
            UUID meetingOccurrenceId,
            String reason,
            ManualReviewStatus status
    ) {
        static ManualReviewCaseView from(ManualReviewCase reviewCase) {
            return new ManualReviewCaseView(
                    reviewCase.id(),
                    reviewCase.qualityGateDecisionId(),
                    reviewCase.meetingOccurrenceId(),
                    reviewCase.reason(),
                    reviewCase.status()
            );
        }
    }
}
