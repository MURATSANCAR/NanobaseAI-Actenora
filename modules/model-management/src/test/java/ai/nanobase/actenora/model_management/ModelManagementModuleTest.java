package ai.nanobase.actenora.model_management;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelManagementModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("model-management", ModelManagementModule.name());
    }
}
