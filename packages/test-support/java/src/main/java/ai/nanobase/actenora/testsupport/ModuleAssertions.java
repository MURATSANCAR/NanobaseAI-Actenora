package ai.nanobase.actenora.testsupport;

/**
 * Shared assertion helpers for architecture / module smoke tests.
 */
public final class ModuleAssertions {

    private ModuleAssertions() {
    }

    public static void requireModuleName(String expected, String actual) {
        if (expected == null || actual == null || !expected.equals(actual)) {
            throw new AssertionError("Expected module '" + expected + "' but was '" + actual + "'");
        }
    }
}
