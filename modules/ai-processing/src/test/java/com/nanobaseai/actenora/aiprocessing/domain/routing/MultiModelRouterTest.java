package com.nanobaseai.actenora.aiprocessing.domain.routing;

import com.nanobaseai.actenora.aiprocessing.application.MultiModelRoutingHarness;
import com.nanobaseai.actenora.aiprocessing.application.MultiModelRoutingService;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.DefaultModelRoleBootstrap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiModelRouterTest {

    private MultiModelRoutingHarness harness;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        harness = new MultiModelRoutingHarness(false);
        tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    }

    @Test
    void chunkExtractionUsesFastExtractionMockWhenNoRealSecondModel() {
        MultiModelRoutingService.RoutingResult result = route(
                InferenceTaskType.CHUNK_EXTRACTION,
                false,
                Set.of(),
                true,
                true,
                false);

        assertEquals(ModelRole.FAST_EXTRACTION, result.decision().requestedRole());
        assertEquals(FallbackStep.PRIMARY, result.fallbackStep());
        assertEquals(DefaultModelRoleBootstrap.FAST_EXTRACTION_MODEL_KEY, result.decision().selectedModelKey().orElseThrow());
        assertTrue(harness.catalog.findByDeploymentId(result.decision().selectedDeploymentId().orElseThrow()).orElseThrow().mock());
    }

    @Test
    void candidateMergeAndFinalNoteUseQwen27Final() {
        assertEquals(
                ModelRole.QWEN27_FINAL,
                route(InferenceTaskType.CANDIDATE_MERGE, false, Set.of(), true, true, false)
                        .decision().requestedRole());
        assertEquals(
                ModelRole.QWEN27_FINAL,
                route(InferenceTaskType.FINAL_NOTE, false, Set.of(), true, true, false)
                        .decision().requestedRole());
        assertEquals(
                DefaultModelRoleBootstrap.QWEN27_PRIMARY_DEPLOYMENT_ID,
                route(InferenceTaskType.FINAL_NOTE, false, Set.of(), true, true, false)
                        .decision().selectedDeploymentId().orElseThrow());
    }

    @Test
    void validationUsesQwenByDefaultOrSeparateModelPerPolicy() {
        assertEquals(
                ModelRole.QWEN27_FINAL,
                route(InferenceTaskType.VALIDATION, false, Set.of(), true, true, false)
                        .decision().requestedRole());

        TenantRoutingPolicy separate = new TenantRoutingPolicy(
                tenantId,
                Set.of(),
                true,
                true,
                ValidationModelPreference.SEPARATE_VALIDATION_MODEL,
                false,
                ConsensusMode.OFF);
        RoutingRequest request = new RoutingRequest(
                UUID.randomUUID(), tenantId, InferenceTaskType.VALIDATION, false, UUID.randomUUID());
        MultiModelRoutingService.RoutingResult result = harness.service.route(request, separate);
        assertEquals(ModelRole.VALIDATION, result.decision().requestedRole());
        assertEquals(DefaultModelRoleBootstrap.VALIDATION_DEPLOYMENT_ID, result.decision().selectedDeploymentId().orElseThrow());
    }

    @Test
    void primaryUnavailableFallsBackToSameModelSecondDeployment() {
        harness.catalog.markHealthy(DefaultModelRoleBootstrap.QWEN27_PRIMARY_DEPLOYMENT_ID, false);

        MultiModelRoutingService.RoutingResult result = route(
                InferenceTaskType.FINAL_NOTE, false, Set.of(), true, true, false);

        assertEquals(FallbackStep.SAME_MODEL_OTHER_DEPLOYMENT, result.fallbackStep());
        assertEquals(DefaultModelRoleBootstrap.QWEN27_SECONDARY_DEPLOYMENT_ID, result.decision().selectedDeploymentId().orElseThrow());
        assertFalse(result.qualityDowngraded());
        assertTrue(result.provenance().isPresent());
        assertEquals(
                DefaultModelRoleBootstrap.QWEN27_FINAL_MODEL_KEY,
                result.provenance().orElseThrow().toModelKey().orElseThrow());
    }

    @Test
    void alternateModelAllowedWithQualityDowngradeFlag() {
        harness.catalog.markHealthy(DefaultModelRoleBootstrap.QWEN27_PRIMARY_DEPLOYMENT_ID, false);
        harness.catalog.markHealthy(DefaultModelRoleBootstrap.QWEN27_SECONDARY_DEPLOYMENT_ID, false);

        MultiModelRoutingService.RoutingResult result = route(
                InferenceTaskType.FINAL_NOTE,
                false,
                Set.of(DefaultModelRoleBootstrap.FAST_EXTRACTION_MODEL_KEY),
                true,
                true,
                false);

        assertEquals(FallbackStep.ALTERNATE_LOCAL_MODEL, result.fallbackStep());
        assertTrue(result.qualityDowngraded());
        assertEquals(DefaultModelRoleBootstrap.FAST_EXTRACTION_MODEL_KEY, result.decision().selectedModelKey().orElseThrow());
        assertTrue(result.provenance().orElseThrow().qualityDowngraded());
    }

    @Test
    void alternateModelForbiddenSkipsUnapprovedCandidates() {
        harness.catalog.markHealthy(DefaultModelRoleBootstrap.QWEN27_PRIMARY_DEPLOYMENT_ID, false);
        harness.catalog.markHealthy(DefaultModelRoleBootstrap.QWEN27_SECONDARY_DEPLOYMENT_ID, false);

        MultiModelRoutingService.RoutingResult result = route(
                InferenceTaskType.FINAL_NOTE,
                false,
                Set.of(),
                true,
                true,
                false);

        assertEquals(FallbackStep.RETRY_QUEUE, result.fallbackStep());
        assertTrue(result.decision().requiresRetryQueue());
        assertTrue(harness.retryQueue.pendingJobIds().contains(result.decision().jobId()));
        assertTrue(result.decision().candidatesConsidered().stream()
                .anyMatch(c -> "alternate_forbidden".equals(c.rejectReason())));
    }

    @Test
    void criticalJobForbidsQualityDowngrade() {
        harness.catalog.markHealthy(DefaultModelRoleBootstrap.QWEN27_PRIMARY_DEPLOYMENT_ID, false);
        harness.catalog.markHealthy(DefaultModelRoleBootstrap.QWEN27_SECONDARY_DEPLOYMENT_ID, false);

        MultiModelRoutingService.RoutingResult result = route(
                InferenceTaskType.FINAL_NOTE,
                true,
                Set.of(DefaultModelRoleBootstrap.FAST_EXTRACTION_MODEL_KEY),
                true,
                true,
                false);

        assertEquals(FallbackStep.RETRY_QUEUE, result.fallbackStep());
        assertTrue(result.decision().candidatesConsidered().stream()
                .anyMatch(c -> "critical_no_downgrade".equals(c.rejectReason())));
        assertFalse(result.decision().hasProductionRoute());
    }

    @Test
    void shadowExecutionDoesNotAffectProductionResult() {
        MultiModelRoutingService.RoutingResult result = route(
                InferenceTaskType.FINAL_NOTE,
                false,
                Set.of(),
                true,
                true,
                true);

        assertTrue(result.shadowExecution().isPresent());
        UUID productionDeployment = result.decision().selectedDeploymentId().orElseThrow();
        assertEquals(DefaultModelRoleBootstrap.QWEN27_PRIMARY_DEPLOYMENT_ID, productionDeployment);

        ShadowExecution completed = harness.service.completeShadow(
                result.shadowExecution().orElseThrow().shadowId(),
                "shadow-result-ref",
                "schema_match=true");

        assertEquals(ShadowExecution.ShadowStatus.COMPLETED, completed.status());
        assertEquals(
                productionDeployment,
                harness.service.productionDecision(result.decision().jobId()).orElseThrow()
                        .selectedDeploymentId().orElseThrow());
        assertEquals(
                ConsensusMode.OFF,
                TenantRoutingPolicy.defaults(tenantId).consensusMode());
    }

    @Test
    void allUnavailableGoesRetryThenManualReview() {
        harness.catalog.listLocalDeployments()
                .forEach(d -> harness.catalog.markHealthy(d.deploymentId(), false));

        MultiModelRoutingService.RoutingResult result = route(
                InferenceTaskType.CHUNK_EXTRACTION,
                false,
                Set.of(DefaultModelRoleBootstrap.QWEN27_FINAL_MODEL_KEY),
                true,
                true,
                false);

        assertEquals(FallbackStep.RETRY_QUEUE, result.fallbackStep());
        RoutingDecision manual = harness.service.escalateRetryToManualReview(result.decision().jobId());
        assertEquals(FallbackStep.MANUAL_REVIEW, manual.fallbackStep());
        assertTrue(manual.requiresManualReview());
        assertFalse(harness.retryQueue.pendingJobIds().contains(result.decision().jobId()));
    }

    @Test
    void provenanceCorrectnessOnFallback() {
        harness.catalog.markHealthy(DefaultModelRoleBootstrap.QWEN27_PRIMARY_DEPLOYMENT_ID, false);

        MultiModelRoutingService.RoutingResult result = route(
                InferenceTaskType.CANDIDATE_MERGE, false, Set.of(), true, true, false);

        ModelChangeProvenance provenance = result.provenance().orElseThrow();
        assertEquals(DefaultModelRoleBootstrap.QWEN27_PRIMARY_DEPLOYMENT_ID, provenance.fromDeploymentId().orElseThrow());
        assertEquals(DefaultModelRoleBootstrap.QWEN27_SECONDARY_DEPLOYMENT_ID, provenance.toDeploymentId().orElseThrow());
        assertEquals(FallbackStep.SAME_MODEL_OTHER_DEPLOYMENT, provenance.fallbackStep());
        assertEquals(result.decision().decisionId(), provenance.routingDecisionId());
        assertFalse(harness.decisionStore.findProvenanceByJobId(result.decision().jobId()).isEmpty());
    }

    @Test
    void attemptHistoryAndQualityMetricsRecorded() {
        MultiModelRoutingService.RoutingResult result = route(
                InferenceTaskType.FINAL_NOTE, false, Set.of(), true, true, false);
        AttemptRecord started = result.startedAttempt().orElseThrow();
        harness.service.completeAttemptSuccess(result.decision().jobId(), started.attemptId(), 120L, true);

        assertEquals(1, harness.attemptHistoryStore.find(result.decision().jobId()).orElseThrow().attempts().size());
        assertEquals(
                AttemptRecord.AttemptStatus.SUCCEEDED,
                harness.attemptHistoryStore.find(result.decision().jobId()).orElseThrow().attempts().getFirst().status());
        assertEquals(1L, harness.qualityStore.snapshot(DefaultModelRoleBootstrap.QWEN27_FINAL_MODEL_ID).orElseThrow().successCount());
        assertEquals(1.0, harness.qualityStore.snapshot(DefaultModelRoleBootstrap.QWEN27_FINAL_MODEL_ID).orElseThrow().schemaPassRate());
    }

    private MultiModelRoutingService.RoutingResult route(
            InferenceTaskType taskType,
            boolean critical,
            Set<String> approvedAlternates,
            boolean allowDowngrade,
            boolean criticalForbidDowngrade,
            boolean shadow
    ) {
        TenantRoutingPolicy policy = new TenantRoutingPolicy(
                tenantId,
                approvedAlternates,
                allowDowngrade,
                criticalForbidDowngrade,
                ValidationModelPreference.QWEN27_FINAL,
                shadow,
                ConsensusMode.OFF);
        RoutingRequest request = new RoutingRequest(
                UUID.randomUUID(), tenantId, taskType, critical, UUID.randomUUID());
        return harness.service.route(request, policy);
    }
}
