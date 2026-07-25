package com.nanobaseai.actenora.aiprocessing.faz28;

import com.nanobaseai.actenora.aiprocessing.application.MultiModelRoutingHarness;
import com.nanobaseai.actenora.aiprocessing.application.MultiModelRoutingService;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.LocalModelProviderException;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ModelWorkerSession;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ProviderFailureCategory;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.ResolvedInferenceInput;
import com.nanobaseai.actenora.aiprocessing.application.modelworker.WorkerRequestEnvelope;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ContextWindowGuard;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FailureCategory;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.PipelineException;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.routing.FallbackStep;
import com.nanobaseai.actenora.aiprocessing.domain.routing.InferenceTaskType;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ModelChangeProvenance;
import com.nanobaseai.actenora.aiprocessing.domain.routing.RoutingDecision;
import com.nanobaseai.actenora.aiprocessing.domain.routing.RoutingRequest;
import com.nanobaseai.actenora.aiprocessing.domain.routing.TenantRoutingPolicy;
import com.nanobaseai.actenora.aiprocessing.infrastructure.json.LimitedJsonRepair;
import com.nanobaseai.actenora.aiprocessing.infrastructure.llm.MockLocalProvider;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.DefaultModelRoleBootstrap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FAZ 28 model failover + AI failure scenarios (invalid JSON, context overflow, worker restart).
 */
class ModelFailoverAndAiFailureScenarioTest {

    private MultiModelRoutingHarness harness;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        harness = new MultiModelRoutingHarness(false);
        tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    }

    @Test
    void singleQwenDeploymentDown_fallsBackToSecondDeploymentWithProvenance() {
        harness.catalog.markHealthy(DefaultModelRoleBootstrap.QWEN27_PRIMARY_DEPLOYMENT_ID, false);

        MultiModelRoutingService.RoutingResult result = route(InferenceTaskType.FINAL_NOTE, false);

        assertEquals(FallbackStep.SAME_MODEL_OTHER_DEPLOYMENT, result.fallbackStep());
        assertEquals(
                DefaultModelRoleBootstrap.QWEN27_SECONDARY_DEPLOYMENT_ID,
                result.decision().selectedDeploymentId().orElseThrow()
        );
        ModelChangeProvenance provenance = result.provenance().orElseThrow();
        assertEquals(DefaultModelRoleBootstrap.QWEN27_PRIMARY_DEPLOYMENT_ID, provenance.fromDeploymentId().orElseThrow());
        assertEquals(DefaultModelRoleBootstrap.QWEN27_SECONDARY_DEPLOYMENT_ID, provenance.toDeploymentId().orElseThrow());
        assertEquals(result.decision().decisionId(), provenance.routingDecisionId());
        assertFalse(provenance.qualityDowngraded());
    }

    @Test
    void allModelsUnavailable_goesRetryQueueThenManualReview() {
        harness.catalog.listLocalDeployments()
                .forEach(d -> harness.catalog.markHealthy(d.deploymentId(), false));

        MultiModelRoutingService.RoutingResult result = route(InferenceTaskType.CHUNK_EXTRACTION, false);
        assertEquals(FallbackStep.RETRY_QUEUE, result.fallbackStep());

        RoutingDecision manual = harness.service.escalateRetryToManualReview(result.decision().jobId());
        assertEquals(FallbackStep.MANUAL_REVIEW, manual.fallbackStep());
        assertTrue(manual.requiresManualReview());
    }

    @Test
    void invalidJson_repairBoundedThenFails() {
        LimitedJsonRepair repair = new LimitedJsonRepair();
        String repaired = repair.repairOrThrow("```json\n{\"ok\":true}\n```");
        assertTrue(repaired.startsWith("{"));
        PipelineException ex = assertThrows(PipelineException.class, () -> repair.repairOrThrow("NOT_JSON_AT_ALL"));
        assertEquals(FailureCategory.INVALID_JSON, ex.category());
    }

    @Test
    void contextOverflow_rejectedBeforeModelCall() {
        ContextWindowGuard guard = new ContextWindowGuard();
        SegmentInput huge = new SegmentInput(
                "seg-1", 0, "Alice", 0, 1000, "x".repeat(50_000), false
        );
        PipelineException ex = assertThrows(
                PipelineException.class,
                () -> guard.assertTranscriptFitsBudget(
                        List.of(huge),
                        com.nanobaseai.actenora.aiprocessing.domain.pipeline.ChunkingConfig.productionDefaults(2048)
                )
        );
        assertEquals(FailureCategory.CONTEXT_OVERFLOW, ex.category());
    }

    @Test
    void workerRestart_drainRejectsNewWorkThenQuiesces() throws InterruptedException {
        MockLocalProvider provider = new MockLocalProvider();
        ModelWorkerSession session = new ModelWorkerSession("worker-1", provider, 2);
        session.beginDrain();
        assertTrue(session.isDraining());
        assertTrue(session.awaitQuiescence(Duration.ofMillis(200)));

        var ex = assertThrows(
                LocalModelProviderException.class,
                () -> session.submit(
                        WorkerRequestEnvelope.builder()
                                .jobId(UUID.randomUUID())
                                .attemptId(UUID.randomUUID())
                                .taskType(InferenceTaskType.FINAL_NOTE)
                                .modelId(UUID.randomUUID())
                                .servedModelId("mock-model")
                                .build(),
                        ResolvedInferenceInput.of("system", "user")
                )
        );
        assertEquals(ProviderFailureCategory.DRAINING, ex.category());
    }

    private MultiModelRoutingService.RoutingResult route(InferenceTaskType taskType, boolean critical) {
        TenantRoutingPolicy policy = new TenantRoutingPolicy(
                tenantId,
                Set.of(DefaultModelRoleBootstrap.FAST_EXTRACTION_MODEL_KEY),
                true,
                true,
                com.nanobaseai.actenora.aiprocessing.domain.routing.ValidationModelPreference.QWEN27_FINAL,
                false,
                com.nanobaseai.actenora.aiprocessing.domain.routing.ConsensusMode.OFF
        );
        RoutingRequest request = new RoutingRequest(
                UUID.randomUUID(), tenantId, taskType, critical, UUID.randomUUID()
        );
        return harness.service.route(request, policy);
    }
}
