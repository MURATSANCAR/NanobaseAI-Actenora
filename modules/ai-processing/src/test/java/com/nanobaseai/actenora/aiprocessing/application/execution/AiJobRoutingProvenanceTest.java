package com.nanobaseai.actenora.aiprocessing.application.execution;

import com.nanobaseai.actenora.aiprocessing.application.AiJobService;
import com.nanobaseai.actenora.aiprocessing.application.MultiModelRoutingService;
import com.nanobaseai.actenora.aiprocessing.application.admission.DefaultAdmissionController;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderFailureCategory;
import com.nanobaseai.actenora.aiprocessing.application.port.AdmissionController;
import com.nanobaseai.actenora.aiprocessing.application.port.JobRoutingCoordinatorPort;
import com.nanobaseai.actenora.aiprocessing.application.port.LocalModelProviderLocator;
import com.nanobaseai.actenora.aiprocessing.application.port.RoutableCandidate;
import com.nanobaseai.actenora.aiprocessing.application.port.TranscriptSegmentSourcePort;
import com.nanobaseai.actenora.aiprocessing.application.routing.CapabilityModelRouter;
import com.nanobaseai.actenora.aiprocessing.application.scheduling.FairJobScheduler;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJobStatus;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;
import com.nanobaseai.actenora.aiprocessing.domain.routing.AttemptRecord;
import com.nanobaseai.actenora.aiprocessing.domain.routing.FallbackStep;
import com.nanobaseai.actenora.aiprocessing.domain.routing.MultiModelRouter;
import com.nanobaseai.actenora.aiprocessing.domain.routing.RoutingDecision;
import com.nanobaseai.actenora.aiprocessing.infrastructure.llm.MockLocalProvider;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryAiAttemptRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryAiJobRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryModelCatalog;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.InMemoryTenantAiPolicy;
import com.nanobaseai.actenora.aiprocessing.infrastructure.prompt.InMemoryPromptRegistry;
import com.nanobaseai.actenora.aiprocessing.infrastructure.prompt.PromptRegistryInferenceInputResolver;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.DefaultModelRoleBootstrap;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryAttemptHistoryStore;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryLocalDeploymentCatalog;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryModelQualityMetricsStore;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryRetryQueue;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryRoutingDecisionStore;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.InMemoryShadowExecutionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FAZ 15 binding: claimed jobs are routed by role, and the execution outcome lands in routing
 * provenance, attempt history, and model quality metrics.
 */
class AiJobRoutingProvenanceTest {

    private static final String SERVED_MODEL_ID = "qwen-local";

    private final UUID tenantId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-07-25T12:00:00Z");

    private InMemoryAiJobRepository jobs;
    private InMemoryLocalDeploymentCatalog deployments;
    private InMemoryRoutingDecisionStore decisions;
    private InMemoryAttemptHistoryStore attemptHistory;
    private InMemoryModelQualityMetricsStore quality;
    private InMemoryRetryQueue retryQueue;
    private MockLocalProvider provider;
    private AiJobService service;

    @BeforeEach
    void setUp() {
        jobs = new InMemoryAiJobRepository();
        InMemoryAiAttemptRepository attempts = new InMemoryAiAttemptRepository();
        InMemoryModelCatalog catalog = new InMemoryModelCatalog();
        InMemoryTenantAiPolicy policy = new InMemoryTenantAiPolicy();
        catalog.add(new RoutableCandidate(
                UUID.randomUUID(),
                "local-final",
                UUID.randomUUID(),
                "final-a",
                Set.of(AiCapability.FINAL_NOTE),
                8192,
                0,
                Set.of("tr", "en"),
                true,
                true,
                true,
                4,
                0,
                0,
                0.9,
                0.6,
                10
        ));

        var capabilityRouter = new CapabilityModelRouter(catalog, policy);
        var scheduler = new FairJobScheduler(jobs, attempts, policy, capabilityRouter);
        service = new AiJobService(
                new DefaultAdmissionController(jobs, policy, capabilityRouter, scheduler),
                jobs,
                attempts,
                scheduler);

        deployments = new InMemoryLocalDeploymentCatalog();
        decisions = new InMemoryRoutingDecisionStore();
        attemptHistory = new InMemoryAttemptHistoryStore();
        quality = new InMemoryModelQualityMetricsStore();
        retryQueue = new InMemoryRetryQueue();
        provider = new MockLocalProvider(2, true, Set.of(SERVED_MODEL_ID));
    }

    @Test
    void successfulJobRecordsRoutingDecisionAndQualityMetrics() {
        DefaultModelRoleBootstrap.seed(deployments, true);
        AiJobInferenceExecutor executor = executor();
        UUID jobId = submit();

        var outcome = executor.executeNext(now).orElseThrow();

        assertTrue(outcome.succeeded());
        RoutingDecision decision = decisions.findByJobId(jobId).getFirst();
        assertEquals(FallbackStep.PRIMARY, decision.fallbackStep());
        assertEquals(
                DefaultModelRoleBootstrap.QWEN27_FINAL_MODEL_KEY,
                decision.selectedModelKey().orElseThrow());

        List<AttemptRecord> history = attemptHistory.find(jobId).orElseThrow().attempts();
        assertEquals(1, history.size());
        assertEquals(AttemptRecord.AttemptStatus.SUCCEEDED, history.getFirst().status());

        var snapshot = quality.snapshot(DefaultModelRoleBootstrap.QWEN27_FINAL_MODEL_ID).orElseThrow();
        assertEquals(1, snapshot.successCount());
        assertEquals(0, snapshot.failureCount());
    }

    @Test
    void providerFailureIsRecordedAsFailedRoutingAttempt() {
        DefaultModelRoleBootstrap.seed(deployments, true);
        provider.forceFailure(ProviderFailureCategory.READ_TIMEOUT);
        AiJobInferenceExecutor executor = executor();
        UUID jobId = submit();

        var outcome = executor.executeNext(now).orElseThrow();

        assertFalse(outcome.succeeded());
        AttemptRecord attempt = attemptHistory.find(jobId).orElseThrow().attempts().getFirst();
        assertEquals(AttemptRecord.AttemptStatus.FAILED, attempt.status());
        assertEquals(ProviderFailureCategory.READ_TIMEOUT.name(), attempt.failureCategory().orElseThrow());
        assertEquals(
                1,
                quality.snapshot(DefaultModelRoleBootstrap.QWEN27_FINAL_MODEL_ID).orElseThrow().failureCount());
    }

    @Test
    void jobWithoutHealthyDeploymentIsRequeuedWithoutCallingProvider() {
        AiJobInferenceExecutor executor = executor();
        UUID jobId = submit();

        var outcome = executor.executeNext(now).orElseThrow();

        assertFalse(outcome.succeeded());
        assertEquals(ProviderFailureCategory.HEALTH_DEGRADED, outcome.failure().orElseThrow());
        assertTrue(outcome.retryable());
        assertEquals(AiJobStatus.QUEUED, jobs.findById(jobId).orElseThrow().status());
        assertTrue(decisions.findByJobId(jobId).getFirst().requiresRetryQueue());
        assertFalse(retryQueue.pendingJobIds().isEmpty());
        assertTrue(attemptHistory.find(jobId).isEmpty());
    }

    @Test
    void unhealthyPrimaryFailsOverToSecondaryDeployment() {
        DefaultModelRoleBootstrap.seed(deployments, true);
        deployments.markHealthy(DefaultModelRoleBootstrap.QWEN27_PRIMARY_DEPLOYMENT_ID, false);
        AiJobInferenceExecutor executor = executor();
        UUID jobId = submit();

        var outcome = executor.executeNext(now).orElseThrow();

        assertTrue(outcome.succeeded());
        RoutingDecision decision = decisions.findByJobId(jobId).getFirst();
        assertEquals(FallbackStep.SAME_MODEL_OTHER_DEPLOYMENT, decision.fallbackStep());
        assertEquals(
                DefaultModelRoleBootstrap.QWEN27_SECONDARY_DEPLOYMENT_ID,
                decision.selectedDeploymentId().orElseThrow());
        assertFalse(decisions.findProvenanceByJobId(jobId).isEmpty());
    }

    private AiJobInferenceExecutor executor() {
        MultiModelRoutingService routingService = new MultiModelRoutingService(
                new MultiModelRouter(),
                deployments,
                decisions,
                attemptHistory,
                new InMemoryShadowExecutionStore(),
                quality,
                retryQueue,
                Clock.fixed(now, ZoneOffset.UTC));
        JobRoutingCoordinatorPort coordinator = new MultiModelRoutingJobCoordinator(
                routingService, new InMemoryTenantAiPolicy());
        return new AiJobInferenceExecutor(
                service,
                LocalModelProviderLocator.single(provider),
                new PromptRegistryInferenceInputResolver(new InMemoryPromptRegistry()),
                modelDefinitionId -> Optional.of(SERVED_MODEL_ID),
                null,
                TranscriptSegmentSourcePort.empty(),
                coordinator,
                AiJobInferenceExecutor.DEFAULT_MAX_ATTEMPTS,
                AiJobInferenceExecutor.DEFAULT_MAX_TIMEOUT_SECONDS);
    }

    private UUID submit() {
        return service.submit(new AdmissionController.SubmitAiJobCommand(
                tenantId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "FINAL_NOTE",
                JobPriority.NORMAL,
                AiCapability.FINAL_NOTE,
                "prompt-v1",
                "schema-v1",
                "tr",
                1000,
                null,
                UUID.randomUUID(),
                now
        )).job().id();
    }
}
