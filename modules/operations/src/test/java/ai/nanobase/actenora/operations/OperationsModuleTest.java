package ai.nanobase.actenora.operations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OperationsModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("operations", OperationsModule.name());
    }
}
