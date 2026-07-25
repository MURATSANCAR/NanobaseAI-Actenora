package com.nanobaseai.actenora.aiprocessing.application;

import com.nanobaseai.actenora.aiprocessing.domain.routing.MultiModelRouter;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.DefaultModelRoleBootstrap;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryAttemptHistoryStore;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryLocalDeploymentCatalog;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryModelQualityMetricsStore;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryRetryQueue;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryRoutingDecisionStore;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryShadowExecutionStore;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Test / local wiring for multi-model routing without Spring.
 */
public final class MultiModelRoutingHarness {

    public final InMemoryLocalDeploymentCatalog catalog = new InMemoryLocalDeploymentCatalog();
    public final InMemoryRoutingDecisionStore decisionStore = new InMemoryRoutingDecisionStore();
    public final InMemoryAttemptHistoryStore attemptHistoryStore = new InMemoryAttemptHistoryStore();
    public final InMemoryShadowExecutionStore shadowStore = new InMemoryShadowExecutionStore();
    public final InMemoryModelQualityMetricsStore qualityStore = new InMemoryModelQualityMetricsStore();
    public final InMemoryRetryQueue retryQueue = new InMemoryRetryQueue();
    public final MultiModelRoutingService service;
    public final AiProcessingFacade api;

    public MultiModelRoutingHarness(boolean includeRealFastExtraction) {
        DefaultModelRoleBootstrap.seed(catalog, includeRealFastExtraction);
        Clock clock = Clock.fixed(Instant.parse("2026-07-25T18:00:00Z"), ZoneOffset.UTC);
        service = new MultiModelRoutingService(
                new MultiModelRouter(),
                catalog,
                decisionStore,
                attemptHistoryStore,
                shadowStore,
                qualityStore,
                retryQueue,
                clock);
        api = new AiProcessingFacade(service, decisionStore, shadowStore, qualityStore);
    }
}
