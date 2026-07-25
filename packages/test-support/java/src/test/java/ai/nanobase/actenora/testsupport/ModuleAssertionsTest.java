package ai.nanobase.actenora.testsupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ModuleAssertionsTest {

    @Test
    void acceptsMatchingNames() {
        ModuleAssertions.requireModuleName("identity", "identity");
    }

    @Test
    void rejectsMismatch() {
        assertThrows(AssertionError.class, () -> ModuleAssertions.requireModuleName("identity", "tenant"));
    }
}
