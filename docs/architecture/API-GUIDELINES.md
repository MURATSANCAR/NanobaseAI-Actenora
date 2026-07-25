# API-GUIDELINES

**Status:** Locked for Phase 0  
**Date:** 2026-07-25

## 1. Style

- HTTP JSON APIs under `/api/v1/...`
- Resource-oriented URLs; verbs only for genuine actions (`/approve`, `/reject`).
- Java `record` DTOs for request/response; **never** expose JPA entities.

## 2. Versioning

| Change | Approach |
|--------|----------|
| Additive field | Same `/v1` |
| Breaking | `/v2` or new media type; deprecate old with sunset header |

## 3. Error model

```json
{
  "code": "APPROVAL_REQUIRED",
  "message": "Delivery blocked until approval is granted",
  "correlationId": "uuid",
  "details": {}
}
```

Stable machine `code` values; human `message` may localize later.

## 4. AuthN / AuthZ

- Authenticated principal from Identity context.
- Workspace-scoped authorization on every mutating call.
- Service-to-service (future extraction): mTLS or signed service JWT — not user password reuse.

## 5. Idempotency

Mutating POSTs that create work accept `Idempotency-Key` header; stored per workspace.

## 6. Pagination & filtering

Cursor-based pagination for lists; never unbounded dumps of evidence or audit.

## 7. Async commands

Long operations return `202 Accepted` + `{ "workflowId" | "jobId" }` and progress via GET or events.

## 8. Controllers

- Thin: map DTO ↔ application command/query.
- No domain logic, no repository calls from controllers.
- Constructor injection only.

## 9. OpenAPI

Each module contributes OpenAPI fragments; aggregated at api starter. Contract tests lock public paths before UI coupling.

## 10. Forbidden

- Returning aggregates with lazy-loaded collections.
- Cross-module façade calls from controllers (go through application of owning module via published API).
- Embedding raw LLM prompts/responses in list endpoints by default (PII / volume).
