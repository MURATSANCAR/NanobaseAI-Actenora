# FAZ-4 Identity, Tenant & Authentication

**Phase:** FAZ 4 — Tenant, User, Role, Permission ve Authentication  
**Date:** 2026-07-25  
**Status:** Implemented (InMemory persistence; Entra JWT edge + local mock IdP)

## 1. Faz özeti

Multi-tenant güvenlik çekirdeği kuruldu: Tenant registry + membership, Identity users/roles/permission catalog, authenticated principal binding from Entra claims (or local mock headers), Spring Security resource-server wiring, Problem Details for authz failures, suspended-tenant full-block policy.

## 2. Değişen bounded context'ler

- `sharedkernel` — `AuthenticatedPrincipal`, `IdentityClaims`, `TenantSecurityContext`
- `identity` — domain + API + InMemory + controllers + Entra/Mock providers
- `tenant` — domain + API + InMemory + current-tenant API
- `meeting` — `FixedTenantContext` now prefers `TenantSecurityContext`
- `platform-backend` — Spring Security, binding filter, Problem Details advice

## 3. Eklenen/değiştirilen dosyalar (öne çıkanlar)

- Flyway: `identity/V102__identity_core.sql`, `tenant/V112__tenant_core.sql`
- APIs: `GET /api/v1/me`, `GET /api/v1/tenants/current`, `GET /api/v1/users`, role grant/revoke
- Docs: `docs/security/SUSPENDED-TENANT-POLICY.md`, SECURITY-BASELINE AuthN/AuthZ update
- Config: `actenora.security.auth.mode=mock|entra` (mock forbidden on prod)

## 4. Migration'lar

| Schema | Version | Purpose |
|--------|---------|---------|
| identity | V102 | users + user_roles |
| tenant | V112 | tenants + tenant_memberships |

## 5. API değişiklikleri

```text
GET    /api/v1/me
GET    /api/v1/tenants/current
GET    /api/v1/users
POST   /api/v1/users/{id}/roles?role=&expectedVersion=
DELETE /api/v1/users/{id}/roles/{role}?expectedVersion=
```

Tenant ID is never taken from body/query — only from IdP `tid` → tenant mapping after auth.

## 6. Event değişiklikleri

Yok (FAZ 4 messaging stubs unchanged).

## 7. Model/prompt/schema

Yok.

## 8. Güvenlik kontrolleri

- Mock IdP refused on `prod`/`production` profiles (startup failure).
- Suspended tenant → full block (403 Problem Details).
- Cross-tenant UUID guessing denied via tenant-scoped queries + `assertSameTenant`.
- Duplicate Entra oid / tid mappings rejected.
- Optimistic locking on role/tenant mutations.

### Local mock headers

```text
X-Mock-Entra-Oid
X-Mock-Entra-Tid
X-Mock-Email
X-Mock-Display-Name
X-Mock-Global-Admin
```

Provision a tenant with matching `entra_tenant_id` before calling APIs.

## 9. Çalıştırılan komutlar

```bash
export JAVA_HOME=.tools/jdk-21
./mvnw -pl modules/identity,modules/tenant,apps/platform-backend -am test \
  -Dtest=RolePermissionCatalogTest,IdentityApplicationServiceTest,TenantApplicationServiceTest,AuthenticationBindingServiceTest,MockAuthProductionGuardTest \
  -Dsurefire.failIfNoSpecifiedTests=false

./mvnw -pl modules/identity,modules/tenant -am test
```

## 10. Test sonuçları

| Suite | Result |
|-------|--------|
| Identity (7 tests) | PASS |
| Tenant (6 tests) | PASS |
| Platform auth binding + mock-prod guard (8 tests) | PASS |

## 11. Bilinen riskler

- Persistence defaults to **InMemory** (JDBC adapters not yet wired) — Flyway tables ready for FAZ follow-up.
- First login auto-provisions user as `PARTICIPANT` (or `SUPER_ADMIN` with global-admin hint); admin bootstrap UX still needed.
- Entra mode requires `ACTENORA_ENTRA_ISSUER_URI` / audience; not E2E-proven against live Entra in this phase.
- Existing `X-Actenora-Tenant-Id` / `X-Actor-*` header bridges elsewhere are not fully removed yet.

## 12. Service extraction etkisi

Identity/Tenant remain in modular monolith; public façades (`IdentityApi`, `TenantApi`) are the extraction seams.

## 13. Sonraki faza geçiş durumu

FAZ 4 kabul kriterlerinin çekirdeği karşılandı. Sonraki öncelik: JDBC wiring for identity/tenant **veya** FAZ 5 policy auth-binding. Full suite / Compose E2E hâlâ FAZ 29 stop-condition kapsamında.
