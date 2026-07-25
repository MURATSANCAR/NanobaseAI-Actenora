# PROMPT-VERSIONING

**Status:** Locked for Phase 0  
**Date:** 2026-07-25

## 1. Rules

1. Prompts are immutable versions: `promptId` + monotonic `version`.
2. Publishing creates a new version; never edit in place.
3. Each version binds an **output JSON schema** id.
4. Runtime calls must reference `promptVersionId`.
5. Rollback = pin workflow/definition to an older version; no silent mutation.

## 2. Artifact

```text
PromptVersion {
  promptVersionId,
  promptId,
  version,
  template,
  outputSchemaId,
  requiredCapabilities[],
  createdAt, createdBy,
  changelog
}
```

## 3. Validation

- Model output parsed against schema → fail closed on mismatch.
- Evidence IDs in output must ⊆ supplied evidence set (ADR-011).
- System prompts must not instruct inventing evidence.

## 4. Storage

Owned by `prompt` schema. Large templates may live in object storage with hash in DB (ADR-007).

## 5. Testing

Golden fixtures per prompt version; CI fails on schema or evidence-discipline regressions.
