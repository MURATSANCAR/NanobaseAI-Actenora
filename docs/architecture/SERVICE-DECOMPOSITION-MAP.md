# SERVICE-DECOMPOSITION-MAP

**Status:** Locked for Phase 0  
**Date:** 2026-07-25

## Reserved extraction targets (`repo-map.yaml`)

| Reserved path | Likely source module(s) | Trigger to extract |
|---------------|-------------------------|--------------------|
| `services/teams-integration-service` | microsoft-connection | Graph rate limits / credential isolation |
| `services/transcript-worker` | transcript (+ partial microsoft-connection) | Ingest backlog / IO heavy |
| `services/model-worker` | ai-processing + model-management + ai-orchestrator | GPU isolation |
| `services/document-renderer` | template (+ meeting-intelligence) | CPU-heavy render |
| `services/delivery-worker` | delivery | Secret blast radius / send volume |

## Priority

| Priority | Candidate |
|----------|-----------|
| P1 | `model-worker` |
| P1 | `delivery-worker` |
| P2 | `transcript-worker` |
| P2 | `teams-integration-service` |
| P3 | `document-renderer` |

## Keep in monolith longest

`identity`, `tenant`, `policy`, `meeting`, `meeting-intelligence`, `approval`, `audit`, `operations`.

## Preconditions

See `SERVICE-EXTRACTION-PLAYBOOK.md`. Do not extract while compile is red or contracts unstable.
