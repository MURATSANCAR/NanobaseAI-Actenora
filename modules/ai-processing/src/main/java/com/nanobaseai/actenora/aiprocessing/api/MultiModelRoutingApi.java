package com.nanobaseai.actenora.aiprocessing.api;

import com.nanobaseai.actenora.aiprocessing.api.MultiModelRoutingDtos.ModelQualityMetricsView;
import com.nanobaseai.actenora.aiprocessing.api.MultiModelRoutingDtos.ProvenanceView;
import com.nanobaseai.actenora.aiprocessing.api.MultiModelRoutingDtos.RouteJobCommand;
import com.nanobaseai.actenora.aiprocessing.api.MultiModelRoutingDtos.RoutingDecisionView;
import com.nanobaseai.actenora.aiprocessing.api.MultiModelRoutingDtos.ShadowExecutionView;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * FAZ 15 public façade for multi-model routing, local fallback, shadow, and quality metrics.
 */
public interface MultiModelRoutingApi {

    RoutingDecisionView routeJob(RouteJobCommand command);

    RoutingDecisionView escalateToManualReview(UUID jobId);

    List<RoutingDecisionView> listRoutingDecisions(UUID jobId);

    List<ProvenanceView> listProvenance(UUID jobId);

    Optional<ShadowExecutionView> findShadow(UUID jobId);

    List<ModelQualityMetricsView> modelQualityMetrics();
}
