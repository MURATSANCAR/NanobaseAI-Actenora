package com.nanobaseai.actenora.aiprocessing.infrastructure.prompt;

import com.nanobaseai.actenora.aiprocessing.domain.prompt.PublishedPrompt;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryPromptRegistryTest {

    @Test
    void publishCreatesNewImmutableVersionWithoutOverwritingPrior() {
        InMemoryPromptRegistry registry = new InMemoryPromptRegistry();
        PublishedPrompt v1 = registry.requirePublished(InMemoryPromptRegistry.DEFAULT_EXTRACTION_PROMPT_ID);

        PublishedPrompt v2 = registry.publish(
                InMemoryPromptRegistry.DEFAULT_EXTRACTION_PROMPT_ID,
                "new template v2",
                "extraction-output.v1",
                "TRANSCRIPT_EXTRACTION"
        );

        assertEquals(2, v2.version());
        assertNotEquals(v1.promptVersionId(), v2.promptVersionId());
        assertEquals(v1.promptVersionId(), registry.requireByVersionId(v1.promptVersionId()).promptVersionId());
        assertEquals("new template v2", registry.requirePublished(InMemoryPromptRegistry.DEFAULT_EXTRACTION_PROMPT_ID).template());
        assertEquals(2, registry.listVersions(InMemoryPromptRegistry.DEFAULT_EXTRACTION_PROMPT_ID).size());
    }

    @Test
    void unknownVersionIdFailsClosed() {
        InMemoryPromptRegistry registry = new InMemoryPromptRegistry();
        ActenoraException ex = assertThrows(
                ActenoraException.class,
                () -> registry.requireByVersionId("pv-does-not-exist"));
        assertTrue(ex.code().contains("PROMPT_VERSION"));
    }
}
