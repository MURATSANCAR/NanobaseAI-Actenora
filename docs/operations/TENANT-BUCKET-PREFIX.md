# Tenant object-storage key prefix standard (FAZ 2)

**Status:** Locked for local infrastructure  
**Date:** 2026-07-25

## Rule

All tenant-owned objects MUST use this key prefix inside any Actenora bucket:

```text
tenants/{tenantId}/
```

- `tenantId` is the opaque tenant identifier (lowercase kebab/uuid; no path separators).
- Application code builds keys as: `{prefixRoot}/{tenantId}/{domain}/{...}`  
  where `prefixRoot` defaults to `tenants` (`OBJECT_STORAGE_TENANT_PREFIX_ROOT`).

## Examples

| Purpose | Key |
|---------|-----|
| Evidence blob | `tenants/tenant-local-test/evidence/2026/07/item-uuid.bin` |
| Artifact | `tenants/acme-corp/artifacts/plan-v3.pdf` |
| Transcript | `tenants/acme-corp/transcripts/meeting-uuid.json` |

## Local test tenant

| Variable | Default |
|----------|---------|
| `OBJECT_STORAGE_TEST_TENANT_ID` | `tenant-local-test` |
| Seeded prefix | `tenants/tenant-local-test/` |

MinIO init (`infrastructure/minio/init-buckets.sh`) creates shared buckets and seeds `.keep` markers under the test tenant prefix.

## Buckets (local)

| Bucket | Role |
|--------|------|
| `actenora` | Primary / default (`OBJECT_STORAGE_BUCKET`) |
| `actenora-artifacts` | Large artifacts |
| `actenora-evidence` | Evidence blobs |
| `actenora-transcripts` | Transcript payloads |

Do **not** encode tenant identity only in bucket names for multi-tenant local/dev; use the key prefix standard so a single adapter can enforce isolation.
