package ai.nanobase.actenora.approval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApprovalModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("approval", ApprovalModule.name());
    }
}
