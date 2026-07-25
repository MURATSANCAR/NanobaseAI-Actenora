# FAZ 1 — Monorepo Bootstrap ve Build Orchestration

**Tarih:** 2026-07-25  
**Durum:** Tamamlandı (orchestration yeşil; domain modülleri paralel fazlarla birlikte evriliyor)

## 1. Faz özeti

Greenfield repoda hedef monorepo iskeleti kuruldu: `apps/`, `modules/`, `packages/`, `services/` (reserved), `infrastructure/`, `docs/`, root script seti, Maven Wrapper, pnpm workspace, uv lock, Docker multi-stage/non-root şablonları, SBOM üretimi ve CI giriş noktaları.

Boş/yarım **service** placeholder’ları reaktörden ve `services/` altından çıkarıldı (Faz 1 kabulü). Geçici extraction taslakları `artifacts/wip-extraction/` altına taşındı.

## 2. Değişen bounded context’ler

Bu fazda business davranış eklenmedi; yapısal olarak şu BC klasörleri Maven reaktörüne bağlandı:

`identity`, `tenant`, `policy`, `microsoft-connection`, `meeting`, `transcript`, `model-management`, `ai-processing`, `meeting-intelligence`, `approval`, `template`, `delivery`, `audit`, `operations` (+ `shared-kernel`).

## 3. Eklenen / değiştirilen dosyalar (özet)

| Alan | İçerik |
|------|--------|
| Root | `README.md`, `Makefile`, `repo-map.yaml`, `.editorconfig`, `.gitignore`, `.gitattributes`, `.env.example`, `pom.xml`, `package.json`, `pnpm-workspace.yaml`, `pyproject.toml`, `uv.lock`, `pnpm-lock.yaml`, `mvnw` |
| Scripts | `bootstrap`, `build-all`, `test-all`, `lint-all`, `run-local`, `stop-local`, `ci-build`, `ci-test`, `generate-sbom` (+ Windows `.ps1`/`.cmd`) |
| Apps | `platform-backend`, `web-portal`, `teams-meeting-app`, `ai-orchestrator` (+ Dockerfile’lar) |
| Packages | `event-contracts`, `api-contracts`, `observability`, `test-support` |
| Infra | `infrastructure/compose/docker-compose.yml` |
| CI | `.github/workflows/ci.yml` |

## 4. Migration’lar

Yok (Faz 1 scope dışı). Local profilde Flyway kapalı / H2 kullanılabilir.

## 5. API değişiklikleri

Bootstrap yüzeyi: `GET /api/health` (platform-backend), `GET /health` (ai-orchestrator, teams-meeting-app). OpenAPI iskeleti: `packages/api-contracts/openapi/platform-api.yaml`.

## 6. Event değişiklikleri

Sözleşme şemaları: `packages/event-contracts/schemas/*` (ajv ile validate).

## 7. Model / prompt / schema

Uygulama modeli yok. Event/API contract şemaları eklendi.

## 8. Güvenlik kontrolleri

- `.env`, keystore, PEM, `secrets/` gitignore  
- `.env.example` secret-free  
- Docker runtime user non-root (`actenora` / nginx-unprivileged)  
- SBOM: `./scripts/generate-sbom` → `artifacts/sbom/*.cdx.json` (+ Maven cyclonedx plugin platform-backend)

## 9. Çalıştırılan komutlar

```bash
./scripts/bootstrap          # (JDK/Node/uv/syft + pnpm/uv/mvn resolve)
./mvnw -B -Dmaven.test.skip=true package
pnpm -r run build
uv sync --all-packages
uv run pytest ...
pnpm -r run lint
uv run ruff check ...
./scripts/generate-sbom
```

Docker Desktop bu ortamda yoktu; `run-local` compose’u atlayıp process moduna düşecek şekilde yazıldı.

## 10. Test sonuçları ve gerçek build süreleri

Kaynak: `artifacts/local/timings.tsv` (2026-07-25T18:06–18:07Z, wall-clock ≈ saniye çözünürlüğü).

| Komut / hedef | Süre (ms) | Sonuç |
|---|---:|---|
| Node workspace build (contracts + libs + web + teams) | 12 000 | ok |
| Python apps (`uv sync` + compileall) | ~0–1 000 | ok |
| Java packages + shared-kernel | 4 000 | ok |
| Java full reactor (`./mvnw -Dmaven.test.skip=true package`) | 16 000 | ok |
| Node tests | 6 000 | ok |
| Python tests | 2 000 | ok |
| Lint (pnpm -r + ruff) | 4 000 | ok |
| SBOM (syft × 4 apps) | 28 000 | ok |

Not: Maven ikinci koşuda cache ile ~6–7 s’ye indi (`package` exit 0, `platform-backend-0.1.0-SNAPSHOT.jar` üretildi).

## 11. Bilinen riskler

- Aynı anda başka faz ajanları `modules/*` ve portal kodunu değiştiriyor; ara durumlarda Java/TS derlemesi kırılabiliyor. Stabil ölçüm yukarıdaki yeşil koşuya aittir.
- `services/` altına tekrar yarım modül eklenmemeli; extraction Faz’ına kadar reserved kalmalı.
- Docker yoksa `run-local` infra (Postgres/Rabbit/MinIO) başlatmaz.

## 12. Service extraction etkisi

Yok. Extraction slot’ları reserved; yarım servis iskeletleri reaktörde değil.

## 13. Sonraki faza geçiş

- Domain/application katmanı (hexagonal kurallar, ArchUnit) devam edebilir.
- Local compose + Flyway şema sahipliği sonraki ops/data fazlarında.
- CI’da `./scripts/ci-build` ve `./scripts/ci-test` kullanılmalı.
