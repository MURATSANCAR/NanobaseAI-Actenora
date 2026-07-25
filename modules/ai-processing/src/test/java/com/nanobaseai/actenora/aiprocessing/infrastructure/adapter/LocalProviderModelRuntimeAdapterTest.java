package com.nanobaseai.actenora.aiprocessing.infrastructure.adapter;

import com.nanobaseai.actenora.aiprocessing.application.pipeline.ExtractionPipelineService;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.PipelineRunRequest;
import com.nanobaseai.actenora.aiprocessing.application.pipeline.PipelineRunResult;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.infrastructure.llm.MockLocalProvider;
import com.nanobaseai.actenora.aiprocessing.infrastructure.prompt.InMemoryPromptRegistry;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalProviderModelRuntimeAdapterTest {

    @Test
    void qwenBridgeRunsThroughLocalModelProviderWithoutCloudFallback() {
        MockLocalProvider provider = new MockLocalProvider(
                2,
                true,
                Set.of(Qwen27BModelAdapter.SERVED_MODEL_ID)
        );
        provider.setResponse("""
                {
                  "topics": [{"text":"Delivery","evidenceSegmentIds":["seg-1"],"confidence":0.9}],
                  "decisions": [{"text":"Ship Friday","evidenceSegmentIds":["seg-1"],"confidence":0.9}],
                  "actionItems": [],
                  "risks": [],
                  "openQuestions": [],
                  "commitments": [],
                  "qualityFlags": [],
                  "evidenceSegmentIds": ["seg-1"],
                  "confidence": 0.9
                }
                """);

        var runtime = LocalProviderModelRuntimeAdapter.qwen27B(provider, UUID.randomUUID());
        ExtractionPipelineService service = ExtractionPipelineService.create(new InMemoryPromptRegistry(), runtime);

        PipelineRunResult result = service.run(new PipelineRunRequest(
                TenantId.random(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                InMemoryPromptRegistry.DEFAULT_EXTRACTION_PROMPT_ID,
                List.of(new SegmentInput("seg-1", 0, "Alice", 0, 1000, "We decided to ship Friday.", true))
        ));

        assertTrue(result.success());
        assertEquals(Qwen27BModelAdapter.MODEL_VERSION, result.modelVersion());
        assertEquals("pv-meeting-chunk-extraction-v1", result.promptVersionId());
    }
}
