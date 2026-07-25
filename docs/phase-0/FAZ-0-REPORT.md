# Faz 0 raporu

## 1. Faz özeti

Baseline çıkarıldı; ürün meeting-intelligence olarak kilitlendi; zorunlu mimari/AI/security/ops belgeleri ve ADR-001…012 yazıldı. Maven package/test **kırmızı** (shared-kernel) — gerçek loglarla belgelendi. Qwen hard-code envanteri çıkarıldı.

## 2. Değişen bounded context'ler

Kodda mevcut modüller dokümante edildi (identity…operations). Phase 0 docs tarafında business feature tamamlanmadı.

## 3. Eklenen/değiştirilen dosyalar (Phase 0 docs)

- `docs/product/*`, `docs/architecture/*`, `docs/ai/*`, `docs/security/*`, `docs/operations/*`, `docs/reviews/*`, `docs/adr/*`, `docs/phase-0/*`
- `README.md` (ürün + durum)
- `infrastructure/postgres/init/01-schemas.sql` (Flyway şemalarıyla hizalandı)
- `artifacts/phase-0/*` (build/test capture)

## 4. Migration'lar

Yeni business migration eklenmedi. Mevcut Flyway sahipliği `DATA-OWNERSHIP.md` içinde kataloglandı.

## 5. API değişiklikleri

Yok (docs-only).

## 6. Event değişiklikleri

`EVENT-CATALOG.md` v0 kilitlendi; runtime publish yok.

## 7. Model/prompt/schema

Politika + Qwen envanteri. Domain’deki `QWEN27_FINAL` kaldırma planı M3.

## 8. Güvenlik

SECURITY-BASELINE / DATA-CLASSIFICATION kilitli. Secret commit yok.

## 9. Çalıştırılan komutlar

- `./mvnw -DskipTests package` → exit **1**
- `./mvnw test` → exit **1**
- `uv run pytest` (ai-orchestrator) → **3 passed**
- `rg` Qwen hard-codes

## 10. Test sonuçları

Java build kırık; detay `artifacts/phase-0/`. Python health OK.

## 11. Bilinen riskler

Aktif paralel iskelet drift’i; Qwen domain sızıntısı; approval→delivery henüz E2E bağlı değil.

## 12. Service extraction etkisi

Adaylar `repo-map.yaml` reserved_services ile hizalı; extraction yapılmadı.

## 13. Sonraki faza geçiş

**Docs/ADR: hazır.** Kod: önce shared-kernel derlemesini yeşile çek, sonra M3 de-Qwen + dikey dilim.
