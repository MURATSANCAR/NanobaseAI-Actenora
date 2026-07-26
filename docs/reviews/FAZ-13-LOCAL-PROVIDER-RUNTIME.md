# FAZ-13 Local LLM Provider / Inference Runtime

**Phase:** FAZ 13  
**Date:** 2026-07-25  
**Status:** Complete (claim → infer → attempt terminal state, InMemory + config-driven provider)

## 1. Faz özeti

Provider adapter'ları (OpenAI-compatible, vLLM, llama.cpp, mock) zaten vardı; eksik olan “claimed job → inference → attempt/job kapanışı” zinciriydi. Bu turda executor, attempt tamamlama servisi, provider konfigürasyonu ve local-only guard eklendi.

## 2. API

```text
POST /api/v1/ai-jobs/execute-next   (OPERATIONS_MANAGE)
```

`claim-next` yalnızca job'ı kilitler; `execute-next` claim + inference + attempt kapanışını tek turda yapar.

## 3. Bu turda değişenler

- `AiJobInferenceExecutor` — claim → envelope → provider → completeAttempt/failAttempt; `drain(n)`
- `AiJobService.completeAttempt` / `failAttempt` — attempt + job terminal geçişleri tek yerde
- Portlar: `LocalModelProviderLocator`, `ServedModelResolverPort`, `InferenceInputResolverPort`
- `PromptRegistryInferenceInputResolver` + `InMemoryPromptRegistry`'ye merge/final-note/validation prompt seed
- `LocalProviderProperties` + `LocalProviderFactory` — kind seçimi, prod'da mock reddi, ADR-005 local-only host guard
- `AiProcessingPlatformConfiguration` — provider/prompt/resolver/executor bean'leri
- `application.yml` — `actenora.ai.provider.*`

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| Claim edilen job'ın provider'da çalışması | ✓ |
| Attempt SUCCEEDED + job SUCCEEDED | ✓ |
| Retryable hata → job QUEUED | ✓ |
| Kalıcı hata → job DEAD | ✓ (kategori politikası) |
| Max attempt sonrası DEAD | ✓ (default 3) |
| Served model registry'den | ✓ (fallback: route modelKey) |
| Timeout job deadline'ından | ✓ (read-timeout ile sınırlı) |
| Provider konfigürasyonu | ✓ `actenora.ai.provider.*` |
| Prod'da mock reddi | ✓ fail-fast |
| Cloud endpoint reddi | ✓ ADR-005 host guard |
| Auth-bound HTTP | ✓ |
| Prompt kaydı | ✓ InMemory (FAZ 16 prompt pack'e kadar placeholder) |
| Transcript içeriğinin prompt'a bağlanması | deferred (FAZ 14 pipeline) |
| JDBC / kalıcı kuyruk | deferred |

## 5. Testler

- `AiJobInferenceExecutorTest` (6) — success, retryable requeue, permanent DEAD, max attempts, bilinmeyen served model, boş kuyruk
- `LocalProviderFactoryTest` (5) — mock/prod reddi, vLLM + llama.cpp local, cloud reddi
- `AiProcessingAuthBindingTest` (6) — submit → execute-next → SUCCEEDED, boş kuyrukta 204
- Regresyon: ai-processing modülü 75 test yeşil

## 6. Bilinen riskler

- Job path'i prompt'a transkript metni koymuyor; gerçek içerik FAZ 14 pipeline'ında
- Tek provider locator (deployment başına ayrı runtime henüz yok)
- Token sayımı heuristik (provider `usage` yoksa)
- Executor senkron; scheduled poller/worker havuzu yok

## 7. Sonraki faz

FAZ 14 — Extraction pipeline'ın job path'ine bağlanması (chunk binding, schema validation, repair).
