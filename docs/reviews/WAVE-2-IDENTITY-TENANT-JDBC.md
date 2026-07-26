# Wave 2 — Identity + Tenant JDBC adapters

## Scope

Durable PostgreSQL persistence for identity users/roles and tenant registry/memberships, gated by `actenora.persistence.mode=jdbc`. Production tenant context no longer falls back to random UUIDs when security context is required.

## Changes

1. **`JdbcUserRepository`** (`modules/identity/.../infrastructure/persistence/`)
   - Tables: `identity.users`, `identity.user_roles` (Flyway V102)
   - `save`: upsert user row + replace role bindings in one transaction; optimistic version check on update
   - `findById`, `findByEntraObjectId`, `listByTenant` load roles (batch for tenant listing)

2. **`JdbcTenantRepository`** (`modules/tenant/.../infrastructure/persistence/`)
   - Tables: `tenant.tenants`, `tenant.tenant_memberships` (Flyway V112)
   - `save` with optimistic version check; idempotent `saveMembership`

3. **Bean wiring**
   - `IdentityJdbcPersistenceConfiguration` / `TenantJdbcPersistenceConfiguration`
   - Active when `actenora.persistence.mode=jdbc`
   - InMemory `@ConditionalOnMissingBean` adapters yield automatically

4. **Configuration**
   - `application.yml`: `actenora.persistence.mode` (default `inmemory`), `actenora.tenancy.require-security-context` (default `false`)
   - `application-prod.yml`: `persistence.mode=jdbc`, `tenancy.require-security-context=true`

5. **Prod tenant context**
   - `FixedTenantContext` with `requireSecurityContext=true` delegates only to `TenantSecurityContext` (no random UUID fallback)

## Tests

| Test | Module | Coverage |
|------|--------|----------|
| `JdbcUserRepositoryTest` | identity | save/find, roles, tenant list sort, optimistic lock |
| `JdbcTenantRepositoryTest` | tenant | save/find, membership, optimistic lock |
| `FixedTenantContextTest` | meeting | fallback vs strict security-context mode |

Run:

```bash
./mvnw -pl modules/identity,modules/tenant,modules/meeting -am test \
  -Dtest=JdbcUserRepositoryTest,JdbcTenantRepositoryTest,FixedTenantContextTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## Exit criteria

- [x] JDBC repositories implement repository ports against V102/V112 schemas
- [x] `actenora.persistence.mode=jdbc` registers JDBC beans; inmemory remains default for local/test
- [x] Prod profile uses JDBC + strict tenant security context
- [x] Module tests green on H2 (PostgreSQL mode) with Flyway migrations

## Follow-ups

- Platform `@ActiveProfiles("it")` Testcontainers integration tests (Postgres) for end-to-end auth binding with JDBC repos
- Remaining bounded contexts still on InMemory until their Wave JDBC adapters land
