# Suspended Tenant Policy

**Status:** Locked for FAZ 4  
**Date:** 2026-07-25

## Decision

Suspended tenants are **full-blocked**.

When `tenant.status = SUSPENDED`:

- Authentication binding fails (`TenantNotActiveException` → HTTP 403 Problem Details).
- No read access to tenant-scoped APIs (`GET /api/v1/me`, meetings, notes, etc.).
- No write access.
- Actuator health endpoints remain available (platform liveness), but carry no tenant data.

## Rationale

1. Suspension is an operational / compliance control, not a soft feature flag.
2. Partial read-only access would still expose transcript-derived intelligence and PII.
3. Reactivation is an explicit admin action (`TenantApi.activate`) with optimistic locking.

## Alternatives considered

| Option | Outcome |
|--------|---------|
| Read-only mode | Rejected — still exposes L2/L3 data |
| Allow auditors only | Deferred — can be added later as a dedicated `SUSPENDED_AUDIT` capability |
| Soft delete | Out of scope — use retention + legal hold (operations) |

## Enforcement points

1. `AuthenticationBindingService` — rejects non-ACTIVE tenants before principal binding.
2. `TenantApi.requireActive` — used by `GET /api/v1/tenants/current` and future callers.
3. Never trust `X-Actenora-Tenant-Id` / body / query tenant IDs for authorization.
