# Flyway version bands (schema-per-context)

Single Flyway history requires **globally unique** version numbers. Each bounded
context owns a numeric band. Prefer the **expanded** headroom ranges for new
migrations when the legacy band is tight.

| Schema | Legacy band | Expanded headroom | Notes |
|--------|-------------|-------------------|-------|
| identity | 100–109 | — | |
| tenant | 110–119 | — | |
| policy | 120–129 | — | |
| microsoftconnection | 130–139 | — | |
| meeting | 140–149 | — | supports `V140_1` style |
| transcript | 150–159 | — | |
| modelmanagement | 160–169 | — | |
| aiprocessing | 170–179 | — | |
| meetingintelligence | 180–189 | **240–259** | legacy nearly full; use 240+ next |
| approval | 190–199 | — | |
| template | 200–209 | — | |
| delivery | 210–219 | — | |
| audit | 220–229 | — | |
| operations | 230–239 | — | |

Enforced by `FlywayMigrationUniquenessTest` (per-schema, global, bands, stale `target/classes`).
