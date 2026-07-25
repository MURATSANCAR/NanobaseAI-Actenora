package com.nanobaseai.actenora.security.messaging;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards against duplicate Flyway version numbers within a single schema migration folder.
 */
class FlywayMigrationUniquenessTest {

    private static final Pattern VERSION = Pattern.compile("^V(\\d+(?:_\\d+)?)__.*\\.sql$");

    @Test
    void sourceMigrationsHaveUniqueVersionsPerSchema() throws IOException {
        Path modules = Path.of("").toAbsolutePath();
        // platform-backend cwd when running tests is apps/platform-backend
        Path repoRoot = modules.getFileName().toString().equals("platform-backend")
                ? modules.getParent().getParent()
                : modules;
        Path modulesRoot = repoRoot.resolve("modules");
        assertTrue(Files.isDirectory(modulesRoot), "modules root not found: " + modulesRoot);

        Map<String, Set<String>> collisions = new HashMap<>();
        try (Stream<Path> schemas = Files.walk(modulesRoot, 8)) {
            schemas
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().equals("migration")
                            || p.getParent() != null
                            && p.getParent().getFileName().toString().equals("migration"))
                    .filter(p -> p.toString().contains("/src/main/resources/db/migration/"))
                    .filter(p -> !p.getFileName().toString().equals("migration"))
                    .forEach(schemaDir -> collectCollisions(schemaDir, collisions));
        }

        if (!collisions.isEmpty()) {
            fail("Duplicate Flyway versions: " + collisions);
        }
    }

    private static void collectCollisions(Path schemaDir, Map<String, Set<String>> collisions) {
        Set<String> seen = new HashSet<>();
        Set<String> dups = new HashSet<>();
        try (Stream<Path> files = Files.list(schemaDir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .forEach(file -> {
                        Matcher m = VERSION.matcher(file.getFileName().toString());
                        if (!m.matches()) {
                            return;
                        }
                        String version = m.group(1);
                        if (!seen.add(version)) {
                            dups.add(version);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (!dups.isEmpty()) {
            collisions.put(schemaDir.toString(), dups);
        }
    }
}
