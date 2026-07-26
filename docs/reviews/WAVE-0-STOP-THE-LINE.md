# Wave 0 — Stop-the-line (enterprise prod master plan)

## Scope

Unblock JDBC/Rabbit seams and harden CI before durable adapters land.

## Changes

1. **Flyway**
   - Fixed `baselines-on-migrate` → `baseline-on-migrate` + `baseline-version: 0`
   - Declared all 14 module schemas + `create-schemas`
   - Postgres init: extensions in schema `extensions` (public less polluted)
   - Strengthened `FlywayMigrationUniquenessTest` (per-schema, global, bands, stale `target/classes`)
   - Expanded meeting-intelligence headroom to **240–259** ([FLYWAY-VERSION-BANDS.md](../operations/FLYWAY-VERSION-BANDS.md))

2. **ArchUnit / Permission**
   - Moved `Permission` to `identity.api` (public façade); domain catalog imports API type

3. **Platform seam**
   - `@ConditionalOnMissingBean` on InMemory beans in:
     - `EventBackbonePlatformConfiguration`
     - `MeetingIntelligencePlatformConfiguration`
     - `AiProcessingPlatformConfiguration`
     - `ApprovalPlatformConfiguration`

4. **CI**
   - Dependency scan hard-fail (`continue-on-error` removed; pnpm audit fails unless allowlisted)
   - gitleaks installed in CI; ArchUnit + Flyway uniqueness as separate job
   - `react-router` / `react-router-dom` pinned to `7.18.1` (fixes GHSA-337j); RSC-only GHSA-qwww allowlisted in [`config/security/audit-allowlist.txt`](../../config/security/audit-allowlist.txt) until `react-router-dom@8` exists
   - `/api/health` anonymous for probes (filter + ENTRA permitAll)

5. **Testcontainers readiness**
   - BOM + postgresql/rabbitmq/junit-jupiter in parent + platform-backend
   - `application-it.yml` keeps Flyway **enabled** for the `it` profile (unit `test` profile still H2/Flyway-off)

## Exit criteria

- ArchUnit / Modulith (BC API leaks) / Flyway uniqueness green
- Health + Portal binding green; web-portal 13/13
- Platform InMemory beans yield when JDBC adapters register the same types (Wave 1)
