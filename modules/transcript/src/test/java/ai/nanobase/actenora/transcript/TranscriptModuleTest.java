package ai.nanobase.actenora.transcript;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TranscriptModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("transcript", TranscriptModule.name());
    }
}
