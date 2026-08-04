package com.nanobaseai.actenora.aiprocessing.application.execution;

import com.nanobaseai.actenora.aiprocessing.application.MultiModelRoutingService;
import com.nanobaseai.actenora.aiprocessing.application.port.JobRoutingCoordinatorPort;
import com.nanobaseai.actenora.aiprocessing.application.port.TenantAiPolicyPort;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ConsensusMode;
import com.nanobaseai.actenora.aiprocessing.domain.routing.InferenceTaskType;
import com.nanobaseai.actenora.aiprocessing.domain.routing.RoutingDecision;
import com.nanobaseai.actenora.aiprocessing.domain.routing.RoutingRequest;
import com.nanobaseai.actenora.aiprocessing.domain.routing.TenantRoutingPolicy;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ValidationModelPreference;

import java.util.Objects;

/**
 * Binds the FAZ 12/13 job execution path to the FAZ 15 multi-model router: every claimed job gets a
 * role-based routing decision with provenance, and every attempt outcome lands in attempt history
 * and model quality metrics.
 */
public final class MultiModelRoutingJobCoordinator implements JobRoutingCoordinatorPort {

    private final MultiModelRoutingService routingService;
    private final TenantAiPolicyPort tenantPolicy;
    private final boolean shadowExecutionEnabled;

    public MultiModelRoutingJobCoordinator(
            MultiModelRoutingService routingService,
            TenantAiPolicyPort tenantPolicy
    ) {
        this(routingService, tenantPolicy, false);
    }

    public MultiModelRoutingJobCoordinator(
            MultiModelRoutingService routingService,
            TenantAiPolicyPort tenantPolicy,
            boolean shadowExecutionEnabled
    ) {
        this.routingService = Objects.requireNonNull(routingService, "routingService");
        this.tenantPolicy = Objects.requireNonNull(tenantPolicy, "tenantPolicy");
        this.shadowExecutionEnabled = shadowExecutionEnabled;
    }

    @Override
    public RoutedExecution routeForExecution(AiJob job, InferenceTaskType taskType) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(taskType, "taskType");

        boolean critical = job.priority() == JobPriority.CRITICAL;
        MultiModelRoutingService.RoutingResult result = routingService.route(
                new RoutingRequest(job.id(), job.tenantId(), taskType, critical, job.correlationId()),
                policyFor(job));

        RoutingDecision decision = result.decision();
        return new RoutedExecution(
                decision.jobId(),
                decision.decisionId(),
                result.startedAttempt().map(attempt -> attempt.attemptId()),
                decision.selectedModelDefinitionId(),
                decision.selectedDeploymentId(),
                decision.selectedModelKey(),
                decision.fallbackStep(),
                decision.qualityDowngraded(),
                decision.requiresRetryQueue(),
                decision.requiresManualReview(),
                decision.reason());
    }

    @Override
    public void recordSuccess(RoutedExecution routed, long latencyMs, boolean schemaPassed) {
        Objects.requireNonNull(routed, "routed");
        routed.attemptId().ifPresent(attemptId ->
                routingService.completeAttemptSuccess(routed.jobId(), attemptId, latencyMs, schemaPassed));
    }

    @Override
    public void recordFailure(RoutedExecution routed, long latencyMs, String failureCategory, String detailSafe) {
        Objects.requireNonNull(routed, "routed");
        routed.attemptId().ifPresent(attemptId ->
                routingService.completeAttemptFailure(
                        routed.jobId(), attemptId, latencyMs, failureCategory, detailSafe));
    }

    private TenantRoutingPolicy policyFor(AiJob job) {
        boolean criticalFallbackAllowed = tenantPolicy.isCriticalFallbackAllowed(job.tenantId());
        return new TenantRoutingPolicy(
                job.tenantId(),
                tenantPolicy.allowedModelKeys(job.tenantId()),
                job.fallbackPermitted(),
                !criticalFallbackAllowed,
                ValidationModelPreference.PRIMARY_QUALITY,
                shadowExecutionEnabled,
                ConsensusMode.OFF);
    }
}
