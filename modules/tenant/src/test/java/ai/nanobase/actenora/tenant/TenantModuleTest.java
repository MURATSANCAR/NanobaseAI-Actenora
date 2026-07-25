package ai.nanobase.actenora.tenant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TenantModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("tenant", TenantModule.name());
    }
}
