package ai.nanobase.actenora.delivery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeliveryModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("delivery", DeliveryModule.name());
    }
}
