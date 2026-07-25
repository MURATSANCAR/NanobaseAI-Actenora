package com.nanobaseai.actenora.aiprocessing.domain.routing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure domain router: walks the local fallback chain and emits an auditable decision + provenance.
 *
 * <pre>
 * PRIMARY → SAME_MODEL_OTHER_DEPLOYMENT → ALTERNATE_LOCAL_MODEL → RETRY_QUEUE → MANUAL_REVIEW
 * </pre>
 */
public final class MultiModelRouter {

    private static final UUID NIL = new UUID(0L, 0L);

    public RoutingOutcome route(
            RoutingRequest request,
            TenantRoutingPolicy policy,
            List<LocalDeploymentRef> catalog,
            Instant decidedAt
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(decidedAt, "decidedAt");
        if (!request.tenantId().equals(policy.tenantId())) {
            throw new IllegalArgumentException("tenant mismatch between request and policy");
        }

        ModelRole requestedRole = TaskRoleMapping.roleFor(request.taskType(), policy);
        List<CandidateEvaluation> evaluations = new ArrayList<>();
        UUID decisionId = UUID.randomUUID();

        List<LocalDeploymentRef> sameRole = catalog.stream()
                .filter(d -> d.role() == requestedRole)
                .sorted(Comparator.comparingInt(LocalDeploymentRef::priority)
                        .thenComparing(LocalDeploymentRef::deploymentKey))
                .toList();

        Optional<LocalDeploymentRef> preferredPrimary = sameRole.stream().findFirst();
        List<LocalDeploymentRef> healthySameRole = sameRole.stream()
                .filter(LocalDeploymentRef::healthy)
                .toList();

        if (!healthySameRole.isEmpty()) {
            LocalDeploymentRef preferred = preferredPrimary.orElseThrow();
            if (preferred.healthy()) {
                evaluations.add(CandidateEvaluation.selected(preferred, FallbackStep.PRIMARY));
                sameRole.stream()
                        .filter(d -> !d.deploymentId().equals(preferred.deploymentId()))
                        .forEach(d -> evaluations.add(CandidateEvaluation.rejected(
                                d,
                                FallbackStep.PRIMARY,
                                d.healthy() ? "lower_priority" : "unhealthy")));
                return success(
                        decisionId,
                        request,
                        requestedRole,
                        FallbackStep.PRIMARY,
                        preferred,
                        false,
                        "primary_local_deployment",
                        evaluations,
                        decidedAt,
                        Optional.empty());
            }

            LocalDeploymentRef secondary = healthySameRole.getFirst();
            sameRole.forEach(d -> {
                if (d.deploymentId().equals(secondary.deploymentId())) {
                    evaluations.add(CandidateEvaluation.selected(d, FallbackStep.SAME_MODEL_OTHER_DEPLOYMENT));
                } else {
                    evaluations.add(CandidateEvaluation.rejected(
                            d,
                            d.deploymentId().equals(preferred.deploymentId())
                                    ? FallbackStep.PRIMARY
                                    : FallbackStep.SAME_MODEL_OTHER_DEPLOYMENT,
                            d.healthy() ? "not_selected" : "unhealthy"));
                }
            });
            return success(
                    decisionId,
                    request,
                    requestedRole,
                    FallbackStep.SAME_MODEL_OTHER_DEPLOYMENT,
                    secondary,
                    false,
                    "same_model_other_local_deployment",
                    evaluations,
                    decidedAt,
                    Optional.of(preferred));
        }

        sameRole.forEach(d -> evaluations.add(CandidateEvaluation.rejected(d, FallbackStep.PRIMARY, "unhealthy")));

        double primaryQuality = sameRole.stream()
                .mapToDouble(LocalDeploymentRef::qualityScore)
                .max()
                .orElse(1.0);
        List<UUID> primaryModelIds = sameRole.stream()
                .map(LocalDeploymentRef::modelDefinitionId)
                .distinct()
                .toList();
        boolean forbidDowngrade = CriticalFallbackPolicy.forbidsQualityDowngrade(request, policy);

        List<LocalDeploymentRef> approvedAlternates = catalog.stream()
                .filter(LocalDeploymentRef::healthy)
                .filter(d -> primaryModelIds.stream().noneMatch(id -> id.equals(d.modelDefinitionId())))
                .filter(d -> policy.isAlternateApproved(d.modelKey()))
                .sorted(Comparator.comparingDouble(LocalDeploymentRef::qualityScore).reversed()
                        .thenComparingInt(LocalDeploymentRef::priority))
                .toList();

        for (LocalDeploymentRef candidate : approvedAlternates) {
            boolean downgrade = candidate.qualityScore() < primaryQuality;
            if (downgrade && forbidDowngrade) {
                evaluations.add(CandidateEvaluation.rejected(
                        candidate,
                        FallbackStep.ALTERNATE_LOCAL_MODEL,
                        request.critical() ? "critical_no_downgrade" : "quality_downgrade_forbidden"));
                continue;
            }
            evaluations.add(CandidateEvaluation.selected(candidate, FallbackStep.ALTERNATE_LOCAL_MODEL));
            return success(
                    decisionId,
                    request,
                    requestedRole,
                    FallbackStep.ALTERNATE_LOCAL_MODEL,
                    candidate,
                    downgrade,
                    downgrade
                            ? "tenant_approved_alternate_quality_downgrade"
                            : "tenant_approved_alternate_local_model",
                    evaluations,
                    decidedAt,
                    preferredPrimary);
        }

        catalog.stream()
                .filter(LocalDeploymentRef::healthy)
                .filter(d -> primaryModelIds.stream().noneMatch(id -> id.equals(d.modelDefinitionId())))
                .filter(d -> !policy.isAlternateApproved(d.modelKey()))
                .forEach(d -> evaluations.add(CandidateEvaluation.rejected(
                        d, FallbackStep.ALTERNATE_LOCAL_MODEL, "alternate_forbidden")));

        evaluations.add(new CandidateEvaluation(
                NIL,
                NIL,
                "retry-queue",
                requestedRole,
                FallbackStep.RETRY_QUEUE,
                true,
                ""));

        RoutingDecision decision = new RoutingDecision(
                decisionId,
                request.jobId(),
                request.tenantId(),
                request.correlationId(),
                request.taskType(),
                requestedRole,
                FallbackStep.RETRY_QUEUE,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false,
                true,
                false,
                "enqueue_retry_queue",
                evaluations,
                decidedAt);

        ModelChangeProvenance provenance = new ModelChangeProvenance(
                UUID.randomUUID(),
                request.jobId(),
                decisionId,
                preferredPrimary.map(LocalDeploymentRef::modelDefinitionId),
                preferredPrimary.map(LocalDeploymentRef::deploymentId),
                preferredPrimary.map(LocalDeploymentRef::modelKey),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                FallbackStep.RETRY_QUEUE,
                false,
                "all_local_routes_exhausted",
                decidedAt);

        return new RoutingOutcome(decision, Optional.of(provenance), true, false);
    }

    /**
     * Escalates a retry-queue decision to manual review (terminal).
     */
    public RoutingDecision escalateToManualReview(RoutingDecision prior, Instant decidedAt) {
        Objects.requireNonNull(prior, "prior");
        Objects.requireNonNull(decidedAt, "decidedAt");
        if (!prior.requiresRetryQueue()) {
            throw new IllegalStateException("only retry-queue decisions escalate to manual review");
        }
        List<CandidateEvaluation> evaluations = new ArrayList<>(prior.candidatesConsidered());
        evaluations.add(new CandidateEvaluation(
                NIL,
                NIL,
                "manual-review",
                prior.requestedRole(),
                FallbackStep.MANUAL_REVIEW,
                true,
                ""));
        return new RoutingDecision(
                UUID.randomUUID(),
                prior.jobId(),
                prior.tenantId(),
                prior.correlationId(),
                prior.taskType(),
                prior.requestedRole(),
                FallbackStep.MANUAL_REVIEW,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                prior.qualityDowngraded(),
                false,
                true,
                "manual_review_after_retry_exhaustion",
                evaluations,
                decidedAt);
    }

    private static RoutingOutcome success(
            UUID decisionId,
            RoutingRequest request,
            ModelRole requestedRole,
            FallbackStep step,
            LocalDeploymentRef selected,
            boolean qualityDowngraded,
            String reason,
            List<CandidateEvaluation> evaluations,
            Instant decidedAt,
            Optional<LocalDeploymentRef> fromPrimary
    ) {
        RoutingDecision decision = new RoutingDecision(
                decisionId,
                request.jobId(),
                request.tenantId(),
                request.correlationId(),
                request.taskType(),
                requestedRole,
                step,
                Optional.of(selected.modelDefinitionId()),
                Optional.of(selected.deploymentId()),
                Optional.of(selected.modelKey()),
                qualityDowngraded,
                false,
                false,
                reason,
                evaluations,
                decidedAt);

        Optional<ModelChangeProvenance> provenance = Optional.empty();
        if (step != FallbackStep.PRIMARY) {
            provenance = Optional.of(new ModelChangeProvenance(
                    UUID.randomUUID(),
                    request.jobId(),
                    decisionId,
                    fromPrimary.map(LocalDeploymentRef::modelDefinitionId),
                    fromPrimary.map(LocalDeploymentRef::deploymentId),
                    fromPrimary.map(LocalDeploymentRef::modelKey),
                    Optional.of(selected.modelDefinitionId()),
                    Optional.of(selected.deploymentId()),
                    Optional.of(selected.modelKey()),
                    step,
                    qualityDowngraded,
                    reason,
                    decidedAt));
        }

        return new RoutingOutcome(decision, provenance, false, false);
    }

    public record RoutingOutcome(
            RoutingDecision decision,
            Optional<ModelChangeProvenance> provenance,
            boolean enqueueRetry,
            boolean manualReview
    ) {
        public RoutingOutcome {
            Objects.requireNonNull(decision, "decision");
            Objects.requireNonNull(provenance, "provenance");
        }
    }
}
