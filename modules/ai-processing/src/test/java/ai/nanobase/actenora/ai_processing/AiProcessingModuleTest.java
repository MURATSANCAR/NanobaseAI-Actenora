package ai.nanobase.actenora.ai_processing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiProcessingModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("ai-processing", AiProcessingModule.name());
    }
}
