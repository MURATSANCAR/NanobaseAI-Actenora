# FAZ 26 — Transcript service extraction simulation report

## 1. Faz özeti

Transcript bounded context ayrı Spring Boot deployable (`services/transcript-worker`) olarak
çalıştırıldı. Kendi `transcript` schema'sı, opaque meetingOccurrenceId contract'ı, outbox/inbox,
HTTP timeout/retry, feature flag (`embedded`/`remote`) ve ayrı Docker image ile extraction
simülasyonu tamamlandı. Distributed transaction ve cross-schema query yok.

## 2. Değişen bounded context'ler

- **transcript** — outbox event publish, meeting occurrence contract consumer, deployment mode flag
- **platform** (orchestration) — remote HTTP gateway when `mode=remote`
- **meeting** — dokunulmadı (contract event adı katalogda zaten vardı)

## 3. Eklenen/değiştirilen dosyalar (özet)

- `services/transcript-worker/**` — yeni deployable + Dockerfile + health/Flyway
- `modules/transcript/.../messaging/*` — outbox publisher, MeetingOccurrenceUpserted handler
- `modules/transcript/.../api/TranscriptDeploymentMode.java` — feature flag constants
- `apps/platform-backend/.../platform/extraction/transcript/*` — remote client + gateway
- `packages/event-contracts/schemas/transcript.TranscriptIngested.v1.json`
- `packages/event-contracts/schemas/meeting.MeetingOccurrenceUpserted.v1.json`
- `docs/architecture/TRANSCRIPT-SERVICE-EXTRACTION.md` — rollback + cutover
- Compose profile `extraction`, `repo-map.yaml`, root `pom.xml`

## 4. Migration'lar

Yeni migration eklenmedi. Worker yalnızca mevcut `classpath:db/migration/transcript` konumlarını uygular.

## 5. API değişiklikleri

- Aynı path: `/api/v1/transcripts/**`
- Remote mode: platform proxy → worker
- Meeting ID: query/contract only (`meetingOccurrenceId`)

## 6. Event değişiklikleri

| Event | Rol |
|-------|-----|
| `meeting.MeetingOccurrenceUpserted.v1` | transcript inbox consumer (opaque id) |
| `transcript.TranscriptIngested.v1` | outbox after durable ingest |
| `transcript.TranscriptReady.v1` | outbox after reparse |

## 7. Model/prompt/schema

Yok.

## 8. Güvenlik kontrolleri

- Transcript içeriği event payload'ında yok
- Tenant header bridge korunuyor (`X-Actenora-Tenant-Id`)
- Secret commit edilmedi

## 9. Çalıştırılan komutlar

```bash
./mvnw -pl modules/transcript,services/transcript-worker,apps/platform-backend -am test
```

## 10. Test sonuçları

Geçen suite (JDK 21):

- `TranscriptExtractionSimulationTest` — 4/4 ✅ (contract, duplicate inbox, outbox restart, no cross-schema SQL, no XA)
- `TranscriptRemoteClientTest` — 2/2 ✅ (timeout/retry)
- `TranscriptWorkerApplicationTest` — 3/3 ✅ (ayrı health/readiness)
- `TranscriptIngestionServiceTest` — 17/17 ✅

```bash
export JAVA_HOME=…/jdk-21…
./mvnw -pl modules/transcript test -Dtest=TranscriptExtractionSimulationTest
./mvnw -pl services/transcript-worker test -Dtest=TranscriptWorkerApplicationTest
./mvnw -pl apps/platform-backend test -Dtest=TranscriptRemoteClientTest
```

## 11. Kabul kriterleri

| Kriter | Durum |
|--------|-------|
| cross-schema query yok | ✅ |
| distributed transaction yok | ✅ |
| ayrı deploy başarılı | ✅ (`transcript-worker` module + image) |
| integration test geçer | ✅ (simülasyon suite) |
| rollback dokümante | ✅ |
| extraction report | ✅ (bu dosya) |

## 12. Rollback

1. `ACTENORA_TRANSCRIPT_MODE=embedded`
2. `transcript-worker` durdur
3. Inbox idempotency ile replay güvenli
4. İki writer yasak

Detay: `docs/architecture/TRANSCRIPT-SERVICE-EXTRACTION.md`

## 13. Service extraction etkisi

İlk BC extraction simülasyonu tamam. Production cutover için playbook soak + on-call ownership hâlâ gerekli.
Default monolit `embedded` kalır; Compose `extraction` profili ile worker ayağa kalkar.

## 14. Sonraki faza geçiş

Hazır: model-worker / delivery-worker adayları playbook sırasına göre.
