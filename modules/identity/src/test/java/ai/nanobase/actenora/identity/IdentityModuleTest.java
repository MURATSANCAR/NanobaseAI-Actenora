package ai.nanobase.actenora.identity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentityModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("identity", IdentityModule.name());
    }
}
