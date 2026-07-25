package ai.nanobase.actenora.microsoft_connection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MicrosoftConnectionModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("microsoft-connection", MicrosoftConnectionModule.name());
    }
}
