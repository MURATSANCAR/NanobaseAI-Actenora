package com.nanobaseai.actenora.aiprocessing.application;

import com.nanobaseai.actenora.aiprocessing.api.MultiModelRoutingApi;
import com.nanobaseai.actenora.aiprocessing.api.MultiModelRoutingDtos.CandidateView;
import com.nanobaseai.actenora.aiprocessing.api.MultiModelRoutingDtos.ModelQualityMetricsView;
import com.nanobaseai.actenora.aiprocessing.api.MultiModelRoutingDtos.ProvenanceView;
import com.nanobaseai.actenora.aiprocessing.api.MultiModelRoutingDtos.RouteJobCommand;
import com.nanobaseai.actenora.aiprocessing.api.MultiModelRoutingDtos.RoutingDecisionView;
import com.nanobaseai.actenora.aiprocessing.api.MultiModelRoutingDtos.ShadowExecutionView;
import com.nanobaseai.actenora.aiprocessing.application.port.ModelQualityMetricsPort;
import com.nanobaseai.actenora.aiprocessing.application.port.RoutingDecisionStorePort;
import com.nanobaseai.actenora.aiprocessing.application.port.ShadowExecutionStorePort;
import com.nanobaseai.actenora.aiprocessing.domain.routing.CandidateEvaluation;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ConsensusMode;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ModelChangeProvenance;
import com.nanobaseai.actenora.aiprocessing.domain.routing.RoutingDecision;
import com.nanobaseai.actenora.aiprocessing.domain.routing.RoutingRequest;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ShadowExecution;
import com.nanobaseai.actenora.aiprocessing.domain.routing.TenantRoutingPolicy;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application façade implementing the FAZ 15 multi-model routing API.
 */
public final class AiProcessingFacade implements MultiModelRoutingApi {

    private final MultiModelRoutingService routingService;
    private final RoutingDecisionStorePort decisionStore;
    private final ShadowExecutionStorePort shadowStore;
    private final ModelQualityMetricsPort qualityMetricsPort;

    public AiProcessingFacade(
            MultiModelRoutingService routingService,
            RoutingDecisionStorePort decisionStore,
            ShadowExecutionStorePort shadowStore,
            ModelQualityMetricsPort qualityMetricsPort
    ) {
        this.routingService = Objects.requireNonNull(routingService, "routingService");
        this.decisionStore = Objects.requireNonNull(decisionStore, "decisionStore");
        this.shadowStore = Objects.requireNonNull(shadowStore, "shadowStore");
        this.qualityMetricsPort = Objects.requireNonNull(qualityMetricsPort, "qualityMetricsPort");
    }

    @Override
    public RoutingDecisionView routeJob(RouteJobCommand command) {
        Objects.requireNonNull(command, "command");
        TenantRoutingPolicy policy = new TenantRoutingPolicy(
                command.tenantId(),
                command.approvedAlternateModelKeys(),
                command.allowQualityDowngrade(),
                command.criticalJobsForbidDowngrade(),
                command.validationModelPreference(),
                command.shadowExecutionEnabled(),
                ConsensusMode.OFF);
        RoutingRequest request = new RoutingRequest(
                command.jobId(),
                command.tenantId(),
                command.taskType(),
                command.critical(),
                command.correlationId());
        return toView(routingService.route(request, policy).decision());
    }

    @Override
    public RoutingDecisionView escalateToManualReview(UUID jobId) {
        return toView(routingService.escalateRetryToManualReview(jobId));
    }

    @Override
    public List<RoutingDecisionView> listRoutingDecisions(UUID jobId) {
        return decisionStore.findByJobId(jobId).stream().map(AiProcessingFacade::toView).toList();
    }

    @Override
    public List<ProvenanceView> listProvenance(UUID jobId) {
        return decisionStore.findProvenanceByJobId(jobId).stream().map(AiProcessingFacade::toProvenance).toList();
    }

    @Override
    public Optional<ShadowExecutionView> findShadow(UUID jobId) {
        return shadowStore.findByJobId(jobId).stream()
                .reduce((first, second) -> second)
                .map(AiProcessingFacade::toShadow);
    }

    @Override
    public List<ModelQualityMetricsView> modelQualityMetrics() {
        return qualityMetricsPort.allSnapshots().stream()
                .map(s -> new ModelQualityMetricsView(
                        s.modelDefinitionId(),
                        s.modelKey(),
                        s.role(),
                        s.successCount(),
                        s.failureCount(),
                        s.averageLatencyMs(),
                        s.schemaPassRate()))
                .toList();
    }

    private static RoutingDecisionView toView(RoutingDecision decision) {
        List<CandidateView> candidates = decision.candidatesConsidered().stream()
                .map(AiProcessingFacade::toCandidate)
                .toList();
        return new RoutingDecisionView(
                decision.decisionId(),
                decision.jobId(),
                decision.taskType(),
                decision.requestedRole(),
                decision.fallbackStep(),
                decision.selectedModelDefinitionId(),
                decision.selectedDeploymentId(),
                decision.selectedModelKey(),
                decision.qualityDowngraded(),
                decision.requiresRetryQueue(),
                decision.requiresManualReview(),
                decision.reason(),
                decision.decidedAt(),
                candidates);
    }

    private static CandidateView toCandidate(CandidateEvaluation evaluation) {
        return new CandidateView(
                evaluation.deploymentId(),
                evaluation.modelKey(),
                evaluation.role(),
                evaluation.consideredForStep(),
                evaluation.selected(),
                evaluation.rejectReason());
    }

    private static ProvenanceView toProvenance(ModelChangeProvenance provenance) {
        return new ProvenanceView(
                provenance.provenanceId(),
                provenance.jobId(),
                provenance.routingDecisionId(),
                provenance.fromModelKey(),
                provenance.toModelKey(),
                provenance.fallbackStep(),
                provenance.qualityDowngraded(),
                provenance.reason(),
                provenance.recordedAt());
    }

    private static ShadowExecutionView toShadow(ShadowExecution shadow) {
        return new ShadowExecutionView(
                shadow.shadowId(),
                shadow.jobId(),
                shadow.championDeploymentId(),
                shadow.challengerDeploymentId(),
                shadow.status().name(),
                shadow.challengerResultRef(),
                ConsensusMode.OFF);
    }
}
