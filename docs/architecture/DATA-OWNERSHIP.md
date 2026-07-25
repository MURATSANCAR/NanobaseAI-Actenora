# DATA-OWNERSHIP

**Status:** Locked for Phase 0  
**Date:** 2026-07-25

## Rules

1. Each table belongs to **exactly one** bounded context (schema owner).
2. Other contexts may store **foreign ids as opaque references**, never FK into foreign schemas for write coupling.
3. Cross-context reads use published APIs, integration events, or explicitly versioned read models owned by the consumer.
4. No shared “common entities” JPA jar across modules (ADR-012).

## Schema map (target)

| Schema | Owner BC | Notes |
|--------|----------|-------|
| `identity` | Identity | users, roles, api_keys, sessions |
| `workspace` | Workspace | workspaces, members, settings |
| `evidence` | Evidence | evidence_items, evidence_links, classifications |
| `artifact` | Artifact | artifact_meta, storage_pointers, checksums |
| `knowledge` | Knowledge | entities, relations, projections |
| `case_mgmt` | Case | cases, case_parties, case_status |
| `planning` | Planning | plans, plan_steps, plan_evidence_refs |
| `modelgw` | Model Gateway | inference_jobs, routing_decisions, model_catalog |
| `prompt` | Prompt | prompts, prompt_versions, output_schemas |
| `workflow` | Workflow | workflow_instances, timers, transitions |
| `approval` | Approval | approval_requests, decisions, policies |
| `execution` | Execution | execution_runs, step_results |
| `delivery` | Delivery | delivery_orders, attempts, adapter_configs |
| `notify` | Notification | notifications, templates |
| `audit` | Audit | audit_entries (append-only) |
| `outbox_*` / `inbox_*` | Owning BC | Per-context outbox/inbox tables (ADR-004) |

`platform` owns **no** business tables.

## Planned table ownership (initial catalog)

> Physical tables do not exist yet (greenfield). This catalog is the ownership lock for Phase 1+.

### identity
| Table | Owner | Contains |
|-------|-------|----------|
| `identity.users` | identity | user accounts |
| `identity.roles` | identity | role defs |
| `identity.user_roles` | identity | assignments |
| `identity.api_keys` | identity | hashed keys |
| `identity.sessions` | identity | session tokens meta |

### workspace
| Table | Owner |
|-------|-------|
| `workspace.workspaces` | workspace |
| `workspace.memberships` | workspace |
| `workspace.settings` | workspace |

### evidence
| Table | Owner |
|-------|-------|
| `evidence.evidence_items` | evidence |
| `evidence.evidence_classifications` | evidence |
| `evidence.evidence_refs` | evidence |

### artifact
| Table | Owner |
|-------|-------|
| `artifact.artifacts` | artifact |
| `artifact.checksums` | artifact |

### knowledge
| Table | Owner |
|-------|-------|
| `knowledge.entities` | knowledge |
| `knowledge.relations` | knowledge |

### case_mgmt
| Table | Owner |
|-------|-------|
| `case_mgmt.cases` | case |
| `case_mgmt.case_links` | case |

### planning
| Table | Owner |
|-------|-------|
| `planning.plans` | planning |
| `planning.plan_steps` | planning |
| `planning.plan_evidence_bindings` | planning |

### modelgw
| Table | Owner |
|-------|-------|
| `modelgw.models` | modelgw |
| `modelgw.routing_policies` | modelgw |
| `modelgw.inference_jobs` | modelgw |
| `modelgw.routing_decisions` | modelgw |

### prompt
| Table | Owner |
|-------|-------|
| `prompt.prompts` | prompt |
| `prompt.prompt_versions` | prompt |
| `prompt.output_schemas` | prompt |

### workflow
| Table | Owner |
|-------|-------|
| `workflow.definitions` | workflow |
| `workflow.instances` | workflow |
| `workflow.transitions` | workflow |
| `workflow.timers` | workflow |

### approval
| Table | Owner |
|-------|-------|
| `approval.policies` | approval |
| `approval.requests` | approval |
| `approval.decisions` | approval |

### execution
| Table | Owner |
|-------|-------|
| `execution.runs` | execution |
| `execution.step_results` | execution |

### delivery
| Table | Owner |
|-------|-------|
| `delivery.orders` | delivery |
| `delivery.attempts` | delivery |
| `delivery.adapter_configs` | delivery |

### notify
| Table | Owner |
|-------|-------|
| `notify.notifications` | notify |
| `notify.templates` | notify |

### audit
| Table | Owner |
|-------|-------|
| `audit.entries` | audit |

### Outbox / inbox (per owning schema)

| Table pattern | Owner |
|---------------|-------|
| `<schema>.outbox_messages` | same as schema |
| `<schema>.inbox_messages` | same as schema |

## Forbidden patterns

- `shared.entities` package used by multiple modules’ repositories.
- Flyway script in module A creating tables in schema B.
- Cross-schema foreign keys for transactional consistency across BCs.
