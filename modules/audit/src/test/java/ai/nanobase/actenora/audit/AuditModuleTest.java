package ai.nanobase.actenora.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("audit", AuditModule.name());
    }
}
