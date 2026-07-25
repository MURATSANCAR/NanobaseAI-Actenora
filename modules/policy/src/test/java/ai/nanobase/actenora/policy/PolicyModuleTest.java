package ai.nanobase.actenora.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PolicyModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("policy", PolicyModule.name());
    }
}
