package com.nanobaseai.actenora.security.messaging;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards Flyway version uniqueness (per-schema and global) and allocated version bands.
 * Single Flyway history requires globally unique versions across all module locations.
 */
class FlywayMigrationUniquenessTest {

    private static final Pattern VERSION = Pattern.compile("^V(\\d+(?:_\\d+)?)__.*\\.sql$");

    /**
     * Inclusive integer bands (underscore suffixes like 140_1 map to major 140).
     * meetingintelligence expanded to 240–259 for headroom beyond the legacy 180–189 band.
     */
    private static final Map<String, int[]> SCHEMA_BANDS = Map.ofEntries(
            Map.entry("identity", new int[]{100, 109}),
            Map.entry("tenant", new int[]{110, 119}),
            Map.entry("policy", new int[]{120, 129}),
            Map.entry("microsoftconnection", new int[]{130, 139}),
            Map.entry("meeting", new int[]{140, 149}),
            Map.entry("transcript", new int[]{150, 159}),
            Map.entry("modelmanagement", new int[]{160, 169}),
            Map.entry("aiprocessing", new int[]{170, 179}),
            Map.entry("meetingintelligence", new int[]{180, 259}),
            Map.entry("approval", new int[]{190, 199}),
            Map.entry("template", new int[]{200, 209}),
            Map.entry("delivery", new int[]{210, 219}),
            Map.entry("audit", new int[]{220, 229}),
            Map.entry("operations", new int[]{230, 239})
    );

    @Test
    void sourceMigrationsHaveUniqueVersionsPerSchemaAndGlobally() throws IOException {
        Path modulesRoot = modulesRoot();
        assertTrue(Files.isDirectory(modulesRoot), "modules root not found: " + modulesRoot);

        Map<String, Set<String>> perSchemaCollisions = new HashMap<>();
        Map<String, List<String>> globalVersions = new LinkedHashMap<>();
        List<String> bandViolations = new ArrayList<>();

        try (Stream<Path> schemas = Files.walk(modulesRoot, 8)) {
            schemas
                    .filter(Files::isDirectory)
                    .filter(p -> p.toString().contains("/src/main/resources/db/migration/"))
                    .filter(p -> !p.getFileName().toString().equals("migration"))
                    .filter(p -> p.getParent() != null
                            && p.getParent().getFileName().toString().equals("migration"))
                    .forEach(schemaDir -> scanSchema(schemaDir, perSchemaCollisions, globalVersions, bandViolations));
        }

        if (!perSchemaCollisions.isEmpty()) {
            fail("Duplicate Flyway versions within schema: " + perSchemaCollisions);
        }

        Map<String, List<String>> globalCollisions = new LinkedHashMap<>();
        globalVersions.forEach((version, paths) -> {
            if (paths.size() > 1) {
                globalCollisions.put(version, paths);
            }
        });
        if (!globalCollisions.isEmpty()) {
            fail("Duplicate Flyway versions across schemas (single history requires uniqueness): "
                    + globalCollisions);
        }

        if (!bandViolations.isEmpty()) {
            fail("Flyway versions outside allocated schema bands: " + bandViolations);
        }
    }

    @Test
    void staleTargetClassesMustNotContainDuplicateMigrationVersions() throws IOException {
        Path modulesRoot = modulesRoot();
        Map<String, List<String>> byVersion = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(modulesRoot, 10)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().contains("/target/classes/db/migration/"))
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .forEach(file -> {
                        Matcher m = VERSION.matcher(file.getFileName().toString());
                        if (!m.matches()) {
                            return;
                        }
                        byVersion.computeIfAbsent(m.group(1), k -> new ArrayList<>())
                                .add(file.toString());
                    });
        }
        Map<String, List<String>> dups = new LinkedHashMap<>();
        byVersion.forEach((version, paths) -> {
            if (paths.size() > 1) {
                dups.put(version, paths);
            }
        });
        if (!dups.isEmpty()) {
            fail("Stale target/classes has duplicate Flyway versions (run mvn clean): " + dups);
        }
    }

    private static void scanSchema(
            Path schemaDir,
            Map<String, Set<String>> perSchemaCollisions,
            Map<String, List<String>> globalVersions,
            List<String> bandViolations
    ) {
        String schema = schemaDir.getFileName().toString();
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
                        String relative = schema + "/" + file.getFileName();
                        if (!seen.add(version)) {
                            dups.add(version);
                        }
                        globalVersions.computeIfAbsent(version, k -> new ArrayList<>()).add(relative);
                        assertBand(schema, version, relative, bandViolations);
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (!dups.isEmpty()) {
            perSchemaCollisions.put(schemaDir.toString(), dups);
        }
    }

    private static void assertBand(String schema, String version, String relative, List<String> bandViolations) {
        int[] band = SCHEMA_BANDS.get(schema);
        if (band == null) {
            bandViolations.add(relative + " (unknown schema band)");
            return;
        }
        int major = Integer.parseInt(version.contains("_") ? version.substring(0, version.indexOf('_')) : version);
        // meetingintelligence legacy 180–189 plus expanded 240–259; exclude foreign bands inside 190–239
        if ("meetingintelligence".equals(schema)) {
            boolean inLegacy = major >= 180 && major <= 189;
            boolean inExpanded = major >= 240 && major <= 259;
            if (!inLegacy && !inExpanded) {
                bandViolations.add(relative + " expected 180–189 or 240–259");
            }
            return;
        }
        if (major < band[0] || major > band[1]) {
            bandViolations.add(relative + " expected " + band[0] + "–" + band[1]);
        }
    }

    private static Path modulesRoot() {
        Path modules = Path.of("").toAbsolutePath();
        Path repoRoot = modules.getFileName().toString().equals("platform-backend")
                ? modules.getParent().getParent()
                : modules;
        return repoRoot.resolve("modules");
    }
}
