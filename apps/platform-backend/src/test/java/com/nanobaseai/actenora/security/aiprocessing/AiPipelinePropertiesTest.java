package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.application.pipeline.MinutesFinalizationPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiPipelinePropertiesTest {

    @Test
    void buildsEditorialPolicyEntirelyFromRuntimeProperties() {
        AiPipelineProperties properties = new AiPipelineProperties();
        properties.setFinalizationMode("editorial");
        properties.setFinalizationPromptResource("/custom/prompts/summary.txt");
        properties.setFinalizationPromptVersionId("prompt-version-from-config");
        properties.setFinalizationSchemaVersion("schema-version-from-config");
        properties.setFinalizationTaskType("FINAL_NOTE");
        properties.setFinalizationMaxOutputTokens(640);
        properties.setFinalizationTimeoutSeconds(75);
        properties.setFinalizationFailureMode("deterministic");

        MinutesFinalizationPolicy policy = properties.finalizationPolicy();

        assertEquals(MinutesFinalizationPolicy.Mode.EDITORIAL, policy.mode());
        assertEquals("/custom/prompts/summary.txt", policy.promptResource());
        assertEquals("prompt-version-from-config", policy.promptVersionId());
        assertEquals("schema-version-from-config", policy.schemaVersion());
        assertEquals("FINAL_NOTE", policy.taskType());
        assertEquals(640, policy.maxOutputTokens());
        assertEquals(75, policy.timeoutSeconds());
        assertEquals(MinutesFinalizationPolicy.FailureMode.DETERMINISTIC, policy.failureMode());
    }

    @Test
    void rejectsIncompleteEditorialConfiguration() {
        AiPipelineProperties properties = new AiPipelineProperties();
        properties.setFinalizationMode("editorial");
        properties.setFinalizationFailureMode("deterministic");

        assertThrows(IllegalArgumentException.class, properties::finalizationPolicy);
    }
}
