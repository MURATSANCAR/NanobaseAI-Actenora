# DATA-OWNERSHIP

**Status:** Locked for Phase 3 (FAZ 3)  
**Date:** 2026-07-25

## Rules

1. Each table belongs to **exactly one** bounded context (schema owner).
2. Other contexts may store **foreign ids as opaque references**, never FK into foreign schemas for write coupling.
3. Cross-context reads use published APIs, integration events, or explicitly versioned read models owned by the consumer.
4. No shared “common entities” JPA jar across modules.
5. Flyway scripts live under the owning module: `classpath:db/migration/<schema>/`.
6. Cross-module version numbers are unique across the monolith (e.g. `V100` identity … `V230` operations).

## Schema map

| Schema | Owner BC |
|--------|----------|
| `identity` | identity |
| `tenant` | tenant |
| `policy` | policy |
| `microsoftconnection` | microsoftconnection |
| `meeting` | meeting |
| `transcript` | transcript |
| `modelmanagement` | modelmanagement |
| `aiprocessing` | aiprocessing |
| `meetingintelligence` | meetingintelligence |
| `approval` | approval |
| `template` | template |
| `delivery` | delivery |
| `audit` | audit |
| `operations` | operations |

`sharedkernel` owns **no** business tables.

## Messaging tables (per owning schema)

| Table | Owner |
|-------|-------|
| `<schema>.outbox_messages` | same as schema |
| `<schema>.inbox_messages` | same as schema |

Configured via `FlywayModuleConfiguration` in the operations module (locations listed for every BC).

## Forbidden patterns

- Flyway script in module A creating tables in schema B
- Cross-schema foreign keys for transactional consistency across BCs
- AI Processing accessing `meetingintelligence.*` tables
- Shared JPA entity packages used by multiple modules’ repositories
