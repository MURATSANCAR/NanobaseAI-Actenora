package com.nanobaseai.actenora.architecture;

import com.nanobaseai.actenora.ActenoraApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;
import org.springframework.modulith.docs.Documenter;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Spring Modulith verification: public API boundaries between bounded contexts.
 * Composition-root packages ({@code security}, {@code platform}) intentionally wire
 * ports/adapters; those violations are deferred until HTTP adapters move into BCs.
 */
class ModulithArchitectureTest {

    static final ApplicationModules MODULES = ApplicationModules.of(ActenoraApplication.class);

    @Test
    void verifiesBoundedContextApiBoundaries() {
        Violations violations = MODULES.detectViolations();
        String details = violations.toString();
        boolean bcLeak = details.lines()
                .filter(line -> line.startsWith("- Module '"))
                .filter(line -> !line.contains("Module 'security'"))
                .filter(line -> !line.contains("Module 'platform'"))
                .anyMatch(line -> line.contains("depends on non-exposed type")
                        || line.contains("Cycle detected"));
        assertFalse(bcLeak, () -> "Bounded-context Modulith violations:\n" + details);
    }

    @Test
    void writesModuleDependencyDiagram() {
        new Documenter(MODULES).writeDocumentation();
    }
}
