# INITIAL-GAP-ANALYSIS

**Phase:** 0 — Repository baseline & architecture lock  
**Date:** 2026-07-25  
**Raw capture:** [`artifacts/phase-0/baseline-capture.txt`](../../artifacts/phase-0/baseline-capture.txt)

## 1. Executive verdict

NanobaseAI-Actenora is a **greenfield repository**: empty `main` (no commits at analysis start), **no application source**, **no build system**, **no tests**. Phase 0 locks product scope, bounded contexts, data ownership, and ADRs so Phase 1 can bootstrap without re-litigating architecture. The repository is **not worse** than before: only docs, ADR, README, `.gitignore`, and baseline artifacts were added — **no business feature code**.

## 2. Repository baseline

| Probe | Result |
|-------|--------|
| Git branch | `main`, no commits at start of Phase 0 |
| Remote | `https://github.com/MURATSANCAR/NanobaseAI-Actenora.git` |
| Source trees | None |
| `pom.xml` / `build.gradle*` | Absent |
| `package.json` | Absent |
| `pyproject.toml` / `requirements.txt` | Absent |
| Docker / Compose | Absent |
| `docs/` before Phase 0 | Absent |

### Stack detection

| Technology | Present in repo? |
|------------|------------------|
| Java / Spring / JPA | No (target only) |
| Python | No |
| Node | No |
| Docker | No |
| Database migrations | No |
| Queue client code | No |

### Host tool probe (developer machine)

| Tool | Status |
|------|--------|
| mvn / gradle | Missing |
| npm | Missing |
| python3 | Present |
| pytest | Missing module |
| uv | Present |
| docker | Missing |
| make | Present |

## 3. Build & test execution (real)

Commands were attempted; failures are environmental / missing project — documented honestly.

| Command | Outcome | Excerpt |
|---------|---------|---------|
| `mvn -q verify` | Fail | `command not found: mvn` |
| `npm test` | Fail | `command not found: npm` |
| `python3 -m pytest -q` | Fail | `No module named pytest` |
| `docker compose config` | Fail | `command not found: docker` |

**Conclusion:** There is nothing to build or test yet. CI cannot be green until Phase 1 bootstrap.

## 4. Module & dependency analysis

| Concern | Finding |
|---------|---------|
| Modules | None |
| Cyclic dependencies | N/A |
| Direct DB access across modules | N/A |
| Shared JPA entities | N/A |
| Hard-coded model ids (Qwen) | **None — no source** |

### Qwen hard-coded inventory

| Location | Status |
|----------|--------|
| Application code | No files |
| Config | No files |
| Tests | No files |
| Docker | No files |

**List:** empty. Prevention watch-list lives in `docs/ai/MULTI-MODEL-ARCHITECTURE.md`.

## 5. Secrets / config methods

| Method | Status |
|--------|--------|
| `.env` committed | No |
| Vault integration | Not present |
| Spring config | Not present |
| Hard-coded credentials in tree | None found (empty tree) |

Target methods locked in `SECURITY-BASELINE.md`.

## 6. Test classification (current)

| Class | Count | Notes |
|-------|-------|-------|
| Unit | 0 | — |
| Integration | 0 | — |
| Architecture (ArchUnit) | 0 | Planned with module bootstrap |
| E2E | 0 | — |
| Load | 0 | — |

## 7. Gap vs target architecture

| Target (locked) | Current | Gap |
|-----------------|---------|-----|
| Modular monolith modules | Absent | Bootstrap multi-module Java build |
| Schema-per-BC | Absent | Introduce Postgres + Flyway/Liquibase per schema |
| RabbitMQ + outbox/inbox | Absent | Infra + relay |
| Object storage port | Absent | MinIO + adapter |
| Model Gateway + routing | Absent | Catalog + policies; no Qwen hard-code |
| Approval before delivery | Absent | State machines + gates |
| Evidence-first validators | Absent | Schema + evidence subset checks |
| ArchUnit boundaries | Absent | Add with first modules |
| Operator API/UI | Absent | Phase 1+ |

## 8. Target bounded contexts (locked)

See `BOUNDED-CONTEXTS.md`. Summary: identity, workspace, evidence, artifact, knowledge, case, planning, modelgw, prompt, workflow, approval, execution, delivery, notify, audit, (+ shared-kernel).

## 9. Data ownership

Every planned table has a single owner schema — see `DATA-OWNERSHIP.md`. **Physical tables: 0** today; ownership is locked before creation.

## 10. Service extraction candidates

| Priority | Candidate |
|----------|-----------|
| P1 | Model Gateway |
| P1 | Delivery |
| P2 | Workflow, Evidence |
| P3 | Audit, Approval |

Details: `SERVICE-DECOMPOSITION-MAP.md`.

## 11. Multi-model transition plan

Documented in `MULTI-MODEL-ARCHITECTURE.md` §5 (M0→M5). Phase 0 completes **M0**.

## 12. Transition plan (no code deletion — nothing to delete)

| Step | Phase | Action |
|------|-------|--------|
| 1 | 0 ✓ | Lock docs + ADRs |
| 2 | 1 | Bootstrap Gradle/Maven multi-module skeleton, empty BC modules, ArchUnit |
| 3 | 1 | Compose: Postgres, RabbitMQ, MinIO, llm-runtime |
| 4 | 1 | Identity + workspace + audit vertical |
| 5 | 2 | Evidence + artifact + approval + delivery stub (no real side effects until gated) |
| 6 | 2 | Model Gateway with catalog (default local model via config id) |
| 7 | 3 | Workflow state machine + outbox/inbox |
| 8 | later | Extract per playbook |

**Rule:** Do not invent shared entities “temporarily.” Do not hard-code Qwen in domain.

## 13. Acceptance criteria checklist

| Criterion | Met? |
|-----------|------|
| Build/test result documented | Yes (failures with real output) |
| Target BC list clear | Yes |
| Every data table owner set | Yes (planned catalog) |
| Service extraction candidates set | Yes |
| Qwen hard-coded points listed | Yes (empty / N/A) |
| Multi-model transition plan written | Yes |
| No comprehensive business code added | Yes |
| Repo not left worse | Yes (docs-only + ignore + baseline artifact) |

## 14. Risks

| Risk | Mitigation |
|------|------------|
| Product vertical (ITSM vs ERP) still open | Non-blocking; adapters behind Delivery port |
| Empty repo may confuse contributors | README status banner |
| Host lacks JDK/Docker for Phase 1 | LOCAL-DEVELOPMENT lists prerequisites |

## 15. Phase 0 exit

Architecture and product decisions are **locked**. Next phase may add skeleton code under these ADRs; it must not reopen ADR-001…012 without a superseding ADR.
