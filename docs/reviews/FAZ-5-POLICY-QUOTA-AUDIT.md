# FAZ-5 Policy, Quota, SLA & Audit Core

**Phase:** FAZ 5  
**Date:** 2026-07-25  
**Status:** Implemented (InMemory SoT + auth-bound platform wiring)

## 1. Faz özeti

Policy evaluation, quota/SLA/model allowlist, and append-only audit were Spring-wired and bound to FAZ 4 authentication. Meeting create enforces daily meeting quota via Policy. Quota denials return RFC 7807 Problem Details and emit `POLICY_DENIED` audit events. Policy overrides are audited via `SettingChangeAuditor`.

## 2. Değişen bounded context'ler

- `policy` — `PolicyModuleConfiguration`
- `audit` — `AuditModuleConfiguration` + content-guard on append
- `identity` — `POLICY_ADMINISTER` permission
- `meeting` — `MeetingQuotaPort` + create-path admission
- `platform-backend` — Policy/Audit controllers, Policy-backed adapters, quota Problem Details

## 3. API

```text
GET  /api/v1/policies/current
PUT  /api/v1/policies/current/overrides
GET  /api/v1/audit/resources/{resourceId}
```

Tenant id from `TenantSecurityContext` only.

## 4. Platform adapters

- `TenantModelAllowlistPort` → `PolicyApi.isModelAllowed`
- `TenantAiPolicyPort` → Policy model/SLA/concurrency projection
- `MeetingAuditPort` → `AuditApi.append`
- `MeetingQuotaPort` → daily meeting quota assert + usage increment

## 5. Güvenlik

- Audit metadata strips transcript / private_note / raw_prompt keys
- Override changes write masked before/after via `SettingChangeAuditor`
- Quota exceeded → 429 `application/problem+json` + audit

## 6. Testler

```bash
export JAVA_HOME=.tools/jdk-21
./mvnw -pl modules/policy,modules/audit,modules/meeting,modules/identity,apps/platform-backend -am \
  -Dtest=PolicyEvaluationServiceTest,QuotaProblemDetailsTest,AuditAppendServiceTest,PolicyAuthBindingTest,RolePermissionCatalogTest,MeetingApplicationServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

## 7. Bilinen riskler

- Policy/Audit persistence still InMemory (Flyway V121/V221 ready)
- Not every mutating API path asserts quotas yet (meeting create + concurrency API are wired)
- JDBC ports deferred

## 8. Sonraki faz

FAZ 6 Meeting Core already largely exists; next meaningful work is JDBC for identity/tenant/policy/audit **or** continue pack order into remaining E2E gaps.
