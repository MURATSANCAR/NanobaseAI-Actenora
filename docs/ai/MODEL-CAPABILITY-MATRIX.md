# MODEL-CAPABILITY-MATRIX

**Status:** Locked for Phase 0  
**Date:** 2026-07-25

Capabilities are tags; physical models bind via `modelmanagement.model_definition` / `model_capability`.

## Capability tags

| Tag | Meaning |
|-----|---------|
| `structured_json` | Schema-valid JSON |
| `long_context` | Large transcript packs |
| `reasoning` | Merge / final note quality |
| `extraction` | Chunk/entity extraction |
| `summarization` | Briefs for approvers |
| `multilingual_tr` | Turkish quality |
| `low_latency` | Interactive/fast path |
| `validation` | Second-pass consistency checks |

## Task → required capabilities

| Task type | Required tags | Logical role (target name) |
|-----------|---------------|----------------------------|
| `transcript.extract_chunks` | extraction, low_latency | `FAST_EXTRACTION` |
| `insight.merge_candidates` | reasoning, structured_json | `FINAL_NOTE_PRIMARY` |
| `insight.final_note` | reasoning, structured_json, multilingual_tr | `FINAL_NOTE_PRIMARY` |
| `insight.validate` | validation, structured_json | `VALIDATION` |
| `insight.summarize_for_approver` | summarization, multilingual_tr | `FINAL_NOTE_PRIMARY` or summarizer slot |

## Catalog examples (ops-bound, not domain enums)

| `model_key` | Example served id | Notes |
|-------------|-------------------|-------|
| `local.final-note.default` | qwen3 / qwen2.5 / … | Bound in DB only |
| `local.extract.fast` | smaller local instruct | Fast path |
| `local.validate` | optional second model | When policy opts in |

**Do not** encode `QWEN27_FINAL` as a permanent domain role name — see remediation in `MULTI-MODEL-ARCHITECTURE.md`.
