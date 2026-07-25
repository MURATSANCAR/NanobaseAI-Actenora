package ai.nanobase.actenora.template;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemplateModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("template", TemplateModule.name());
    }
}
