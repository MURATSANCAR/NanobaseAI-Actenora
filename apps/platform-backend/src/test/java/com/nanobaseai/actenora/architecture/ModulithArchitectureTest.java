package com.nanobaseai.actenora.architecture;

import com.nanobaseai.actenora.ActenoraApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Spring Modulith verification: public API boundaries, no cycles, allowed deps only.
 */
class ModulithArchitectureTest {

    static final ApplicationModules MODULES = ApplicationModules.of(ActenoraApplication.class);

    @Test
    void verifiesModularStructure() {
        MODULES.verify();
    }

    @Test
    void writesModuleDependencyDiagram() {
        new Documenter(MODULES).writeDocumentation();
    }
}
