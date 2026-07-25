# NanobaseAI Actenora

Local-first **Microsoft Teams meeting intelligence** platform: Graph-connected meetings → transcripts → multi-model local AI → human approval → governed delivery.

## Status

**FAZ 3:** Spring Modulith + ArchUnit enforce the 14 bounded contexts. Verification:

```bash
./mvnw -pl apps/platform-backend test -Dtest=ModulithArchitectureTest,ModularMonolithArchUnitTest
```

Architecture and ADRs live under `docs/`. Treat broader build/test status in [`docs/reviews/INITIAL-GAP-ANALYSIS.md`](docs/reviews/INITIAL-GAP-ANALYSIS.md) as authoritative for full CI.

## Docs

| Area | Entry |
|------|-------|
| Gap analysis | [`docs/reviews/INITIAL-GAP-ANALYSIS.md`](docs/reviews/INITIAL-GAP-ANALYSIS.md) |
| Product scope | [`docs/product/PRODUCT-SCOPE.md`](docs/product/PRODUCT-SCOPE.md) |
| Bounded contexts | [`docs/architecture/BOUNDED-CONTEXTS.md`](docs/architecture/BOUNDED-CONTEXTS.md) |
| Module owners / extraction | [`docs/architecture/MODULE-OWNERS-AND-EXTRACTION.md`](docs/architecture/MODULE-OWNERS-AND-EXTRACTION.md) |
| Integration events | [`docs/architecture/MODULE-INTEGRATION-EVENTS.md`](docs/architecture/MODULE-INTEGRATION-EVENTS.md) |
| Modulith diagrams | [`docs/architecture/modulith/`](docs/architecture/modulith/) |
| Data ownership | [`docs/architecture/DATA-OWNERSHIP.md`](docs/architecture/DATA-OWNERSHIP.md) |
| ADRs | [`docs/adr/`](docs/adr/) |
| Local dev | [`docs/operations/LOCAL-DEVELOPMENT.md`](docs/operations/LOCAL-DEVELOPMENT.md) |

## Stack (locked)

| Concern | Choice |
|---------|--------|
| Shape | Modular monolith first (ADR-001) + extractable workers |
| Backend | Java 21 / Spring Boot / Spring Modulith modules |
| AI process | Python FastAPI (`ai-orchestrator`) |
| Persistence | PostgreSQL, schema-per-module (ADR-009) |
| Messaging | RabbitMQ + transactional outbox/inbox (ADR-004, ADR-008) |
| Objects | MinIO-compatible via port (ADR-007) |
| LLM | Local-only default; multi-model routing (ADR-005, ADR-006) |
| Delivery | Human approval required (ADR-010) |
| AI output | Evidence-first (ADR-011) |

## Commands

```bash
cp .env.example .env
make bootstrap
make build
make test
make run
```

See `repo-map.yaml` for project registry and reserved service extraction slots.
