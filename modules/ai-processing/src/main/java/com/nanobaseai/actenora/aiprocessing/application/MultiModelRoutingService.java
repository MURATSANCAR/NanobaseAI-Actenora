package com.nanobaseai.actenora.aiprocessing.application;

import com.nanobaseai.actenora.aiprocessing.application.port.AttemptHistoryPort;
import com.nanobaseai.actenora.aiprocessing.application.port.LocalDeploymentCatalogPort;
import com.nanobaseai.actenora.aiprocessing.application.port.ModelQualityMetricsPort;
import com.nanobaseai.actenora.aiprocessing.application.port.RetryQueuePort;
import com.nanobaseai.actenora.aiprocessing.application.port.RoutingDecisionStorePort;
import com.nanobaseai.actenora.aiprocessing.application.port.ShadowExecutionStorePort;
import com.nanobaseai.actenora.aiprocessing.domain.routing.AttemptRecord;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ConsensusMode;
import com.nanobaseai.actenora.aiprocessing.domain.routing.FallbackStep;
import com.nanobaseai.actenora.aiprocessing.domain.routing.LocalDeploymentRef;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ModelChangeProvenance;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ModelRole;
import com.nanobaseai.actenora.aiprocessing.domain.routing.MultiModelRouter;
import com.nanobaseai.actenora.aiprocessing.domain.routing.RoutingDecision;
import com.nanobaseai.actenora.aiprocessing.domain.routing.RoutingRequest;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ShadowExecution;
import com.nanobaseai.actenora.aiprocessing.domain.routing.TenantRoutingPolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service: multi-model routing, fallback orchestration, attempt history, shadow scheduling.
 * Consensus remains OFF by default; shadow results never replace production.
 */
public final class MultiModelRoutingService {

    private final MultiModelRouter router;
    private final LocalDeploymentCatalogPort catalogPort;
    private final RoutingDecisionStorePort decisionStore;
    private final AttemptHistoryPort attemptHistoryPort;
    private final ShadowExecutionStorePort shadowStore;
    private final ModelQualityMetricsPort qualityMetricsPort;
    private final RetryQueuePort retryQueuePort;
    private final Clock clock;

    public MultiModelRoutingService(
            MultiModelRouter router,
            LocalDeploymentCatalogPort catalogPort,
            RoutingDecisionStorePort decisionStore,
            AttemptHistoryPort attemptHistoryPort,
            ShadowExecutionStorePort shadowStore,
            ModelQualityMetricsPort qualityMetricsPort,
            RetryQueuePort retryQueuePort,
            Clock clock
    ) {
        this.router = Objects.requireNonNull(router, "router");
        this.catalogPort = Objects.requireNonNull(catalogPort, "catalogPort");
        this.decisionStore = Objects.requireNonNull(decisionStore, "decisionStore");
        this.attemptHistoryPort = Objects.requireNonNull(attemptHistoryPort, "attemptHistoryPort");
        this.shadowStore = Objects.requireNonNull(shadowStore, "shadowStore");
        this.qualityMetricsPort = Objects.requireNonNull(qualityMetricsPort, "qualityMetricsPort");
        this.retryQueuePort = Objects.requireNonNull(retryQueuePort, "retryQueuePort");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public RoutingResult route(RoutingRequest request, TenantRoutingPolicy policy) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(policy, "policy");
        if (policy.consensusMode() != ConsensusMode.OFF) {
            throw new IllegalArgumentException("consensus must remain OFF in FAZ 15 default path");
        }

        Instant now = clock.instant();
        MultiModelRouter.RoutingOutcome outcome = router.route(
                request,
                policy,
                catalogPort.listLocalDeployments(),
                now);

        RoutingDecision decision = outcome.decision();
        decisionStore.save(decision);
        outcome.provenance().ifPresent(decisionStore::saveProvenance);

        Optional<ShadowExecution> shadow = Optional.empty();
        if (decision.hasProductionRoute() && policy.shadowExecutionEnabled()) {
            shadow = scheduleShadow(decision, policy);
        }

        if (outcome.enqueueRetry()) {
            retryQueuePort.enqueue(decision);
        }

        Optional<AttemptRecord> startedAttempt = Optional.empty();
        if (decision.hasProductionRoute()) {
            startedAttempt = Optional.of(startAttempt(decision));
        }

        return new RoutingResult(decision, outcome.provenance(), startedAttempt, shadow);
    }

    public RoutingDecision escalateRetryToManualReview(UUID jobId) {
        Objects.requireNonNull(jobId, "jobId");
        List<RoutingDecision> prior = decisionStore.findByJobId(jobId);
        RoutingDecision retryDecision = prior.stream()
                .filter(RoutingDecision::requiresRetryQueue)
                .max(Comparator.comparing(RoutingDecision::decidedAt))
                .orElseThrow(() -> new IllegalStateException("no retry-queue decision for job " + jobId));
        RoutingDecision manual = router.escalateToManualReview(retryDecision, clock.instant());
        decisionStore.save(manual);
        retryQueuePort.remove(jobId);
        return manual;
    }

    public AttemptRecord startAttempt(RoutingDecision decision) {
        Objects.requireNonNull(decision, "decision");
        if (!decision.hasProductionRoute()) {
            throw new IllegalStateException("cannot start attempt without production route");
        }
        var history = attemptHistoryPort.getOrCreate(decision.jobId());
        AttemptRecord attempt = new AttemptRecord(
                UUID.randomUUID(),
                decision.jobId(),
                decision.decisionId(),
                history.nextAttemptNumber(),
                decision.selectedModelDefinitionId().orElseThrow(),
                decision.selectedDeploymentId().orElseThrow(),
                decision.selectedModelKey().orElseThrow(),
                decision.requestedRole(),
                decision.fallbackStep(),
                AttemptRecord.AttemptStatus.STARTED,
                decision.qualityDowngraded(),
                Optional.empty(),
                Optional.empty(),
                clock.instant(),
                Optional.empty());
        attemptHistoryPort.append(attempt);
        return attempt;
    }

    public AttemptRecord completeAttemptSuccess(UUID jobId, UUID attemptId, long latencyMs, boolean schemaPassed) {
        AttemptRecord prior = requireAttempt(jobId, attemptId);
        AttemptRecord completed = new AttemptRecord(
                prior.attemptId(),
                prior.jobId(),
                prior.routingDecisionId(),
                prior.attemptNumber(),
                prior.modelDefinitionId(),
                prior.deploymentId(),
                prior.modelKey(),
                prior.role(),
                prior.fallbackStep(),
                AttemptRecord.AttemptStatus.SUCCEEDED,
                prior.qualityDowngraded(),
                Optional.empty(),
                Optional.empty(),
                prior.startedAt(),
                Optional.of(clock.instant()));
        attemptHistoryPort.complete(completed);
        qualityMetricsPort.recordSuccess(
                prior.modelDefinitionId(), prior.modelKey(), prior.role(), latencyMs, schemaPassed);
        return completed;
    }

    public AttemptRecord completeAttemptFailure(UUID jobId, UUID attemptId, long latencyMs, String category, String detailSafe) {
        AttemptRecord prior = requireAttempt(jobId, attemptId);
        AttemptRecord completed = new AttemptRecord(
                prior.attemptId(),
                prior.jobId(),
                prior.routingDecisionId(),
                prior.attemptNumber(),
                prior.modelDefinitionId(),
                prior.deploymentId(),
                prior.modelKey(),
                prior.role(),
                prior.fallbackStep(),
                AttemptRecord.AttemptStatus.FAILED,
                prior.qualityDowngraded(),
                Optional.ofNullable(category),
                Optional.ofNullable(detailSafe),
                prior.startedAt(),
                Optional.of(clock.instant()));
        attemptHistoryPort.complete(completed);
        qualityMetricsPort.recordFailure(prior.modelDefinitionId(), prior.modelKey(), prior.role(), latencyMs);
        return completed;
    }

    /**
     * Completes a shadow run. Explicitly does not mutate production routing decision or attempt history.
     */
    public ShadowExecution completeShadow(
            UUID shadowId,
            String challengerResultRef,
            String comparisonSummarySafe
    ) {
        ShadowExecution prior = shadowStore.findById(shadowId)
                .orElseThrow(() -> new IllegalArgumentException("unknown shadowId"));
        ShadowExecution completed = prior.withChallengerResult(
                challengerResultRef,
                comparisonSummarySafe,
                clock.instant());
        shadowStore.save(completed);
        return completed;
    }

    public Optional<RoutingDecision> productionDecision(UUID jobId) {
        return decisionStore.findByJobId(jobId).stream()
                .filter(RoutingDecision::hasProductionRoute)
                .max(Comparator.comparing(RoutingDecision::decidedAt));
    }

    private Optional<ShadowExecution> scheduleShadow(RoutingDecision decision, TenantRoutingPolicy policy) {
        if (policy.consensusMode() != ConsensusMode.OFF) {
            return Optional.empty();
        }
        UUID championDeploymentId = decision.selectedDeploymentId().orElseThrow();
        Optional<LocalDeploymentRef> challenger = catalogPort.listLocalDeployments().stream()
                .filter(LocalDeploymentRef::healthy)
                .filter(d -> !d.deploymentId().equals(championDeploymentId))
                .filter(d -> d.role() == ModelRole.QWEN27_FINAL || d.role() == decision.requestedRole())
                .sorted(Comparator.comparingInt(LocalDeploymentRef::priority))
                .findFirst();
        if (challenger.isEmpty()) {
            return Optional.empty();
        }
        LocalDeploymentRef c = challenger.get();
        ShadowExecution shadow = new ShadowExecution(
                UUID.randomUUID(),
                decision.jobId(),
                decision.decisionId(),
                championDeploymentId,
                c.deploymentId(),
                decision.selectedModelDefinitionId().orElseThrow(),
                c.modelDefinitionId(),
                ShadowExecution.ShadowStatus.SCHEDULED,
                Optional.empty(),
                Optional.empty(),
                clock.instant(),
                Optional.empty());
        shadowStore.save(shadow);
        return Optional.of(shadow);
    }

    private AttemptRecord requireAttempt(UUID jobId, UUID attemptId) {
        return attemptHistoryPort.find(jobId)
                .orElseThrow(() -> new IllegalArgumentException("unknown job"))
                .attempts()
                .stream()
                .filter(a -> a.attemptId().equals(attemptId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown attempt"));
    }

    public record RoutingResult(
            RoutingDecision decision,
            Optional<ModelChangeProvenance> provenance,
            Optional<AttemptRecord> startedAttempt,
            Optional<ShadowExecution> shadowExecution
    ) {
        public RoutingResult {
            Objects.requireNonNull(decision, "decision");
            Objects.requireNonNull(provenance, "provenance");
            Objects.requireNonNull(startedAttempt, "startedAttempt");
            Objects.requireNonNull(shadowExecution, "shadowExecution");
        }

        public boolean qualityDowngraded() {
            return decision.qualityDowngraded();
        }

        public FallbackStep fallbackStep() {
            return decision.fallbackStep();
        }
    }
}
