package com.nanobaseai.actenora.aiprocessing.application;

import com.nanobaseai.actenora.aiprocessing.api.MultiModelRoutingDtos.RouteJobCommand;
import com.nanobaseai.actenora.aiprocessing.api.MultiModelRoutingDtos.RoutingDecisionView;
import com.nanobaseai.actenora.aiprocessing.domain.routing.FallbackStep;
import com.nanobaseai.actenora.aiprocessing.domain.routing.InferenceTaskType;
import com.nanobaseai.actenora.aiprocessing.domain.routing.ValidationModelPreference;
import com.nanobaseai.actenora.aiprocessing.infrastructure.routing.DefaultModelRoleBootstrap;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiProcessingFacadeRoutingTest {

    @Test
    void apiRouteJobAuditsDecisionAndKeepsConsensusOff() {
        MultiModelRoutingHarness harness = new MultiModelRoutingHarness(false);
        UUID tenantId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        RoutingDecisionView view = harness.api.routeJob(new RouteJobCommand(
                jobId,
                tenantId,
                InferenceTaskType.CHUNK_EXTRACTION,
                false,
                UUID.randomUUID(),
                Set.of(),
                true,
                true,
                ValidationModelPreference.QWEN27_FINAL,
                true));

        assertEquals(FallbackStep.PRIMARY, view.fallbackStep());
        assertEquals(DefaultModelRoleBootstrap.FAST_EXTRACTION_MODEL_KEY, view.selectedModelKey().orElseThrow());
        assertEquals(1, harness.api.listRoutingDecisions(jobId).size());
        assertTrue(harness.api.findShadow(jobId).isPresent());
        assertEquals("OFF", harness.api.findShadow(jobId).orElseThrow().consensusMode().name());
    }
}
